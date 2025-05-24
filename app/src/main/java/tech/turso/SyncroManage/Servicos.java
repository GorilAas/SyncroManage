package tech.turso.SyncroManage;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Servicos extends BaseActivity implements DatabaseManager.ConnectionStateListener, DatabaseManager.DataChangeListener {
    private static final String TAG = "Servicos"; // Tag para logs
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000; // 1 segundo
    private static final int CONNECTION_TIMEOUT_MS = 30000; // 30 segundos

    private RecyclerView recyclerServicos;
    private TextView txtEmpty;
    private SearchView searchServicos;
    private FloatingActionButton fabAddServico;
    private View progressBarServicos;

    private ServicoAdapter adapter;
    private List<Servico> listaServicos = new ArrayList<>();
    private ExecutorService executor;

    private ActivityResultLauncher<Intent> formServicoLauncher;
    private boolean isLoading = false;

    // Variáveis para controle de timeout e retry
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private AtomicBoolean operationCancelled = new AtomicBoolean(false);
    private AtomicInteger retryCount = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servicos);

        // Inicializa o executor como um serviço de thread única
        executor = Executors.newSingleThreadExecutor();
        timeoutHandler = new Handler(Looper.getMainLooper());

        setupNavigationDrawer(
                R.id.drawer_layout,
                R.id.toolbar,
                R.id.nav_view,
                R.id.nav_servico
        );

        initViews();
        setupRecyclerView();
        setupSearchView();
        setupListeners();
        setupFormLauncher();

        // Registra o listener de conexão
        DatabaseManager.addConnectionStateListener(this);

        // Tenta carregar os dados do banco
        initializeDatabase();
    }

    private void initViews() {
        recyclerServicos = findViewById(R.id.recycler_servicos);
        txtEmpty = findViewById(R.id.text_empty);
        searchServicos = findViewById(R.id.search_servicos);
        fabAddServico = findViewById(R.id.fab_add_servico);
        progressBarServicos = findViewById(R.id.progress_bar_servicos);
    }

    private void setupRecyclerView() {
        recyclerServicos.setLayoutManager(new LinearLayoutManager(this));
        recyclerServicos.setHasFixedSize(true); // Otimização para listas de tamanho fixo
        adapter = new ServicoAdapter(listaServicos, this);
        recyclerServicos.setAdapter(adapter);
    }

    private void setupSearchView() {
        searchServicos.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
        });
    }

    private void setupListeners() {
        fabAddServico.setOnClickListener(v -> {
            Intent intent = new Intent(Servicos.this, FormServicoActivity.class);
            formServicoLauncher.launch(intent);
        });

        adapter.setOnItemClickListener(new ServicoAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Servico servico) { abrirFormularioEdicao(servico); }

            @Override
            public void onEditClick(Servico servico) { abrirFormularioEdicao(servico); }

            @Override
            public void onDeleteClick(Servico servico) { confirmarExclusao(servico); }
        });
    }

    private void setupFormLauncher() {
        formServicoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        carregarServicos();
                    }
                });
    }

    private void initializeDatabase() {
        // Resetar controles de timeout e retry
        operationCancelled.set(false);
        retryCount.set(0);

        // Mostra o progresso enquanto verifica o banco
        showLoading(true);

        // Define o timeout
        startConnectionTimeout();

        // Verifica se o banco já está inicializado antes de tentar inicializar
        if (!DatabaseManager.isInitialized()) {
            Log.d(TAG, "Banco de dados não inicializado. Iniciando...");

            DatabaseManager.initializeAsync(this, (success, message) -> {
                // Cancelar o timeout pois recebemos uma resposta
                cancelTimeout();

                if (operationCancelled.get()) {
                    Log.d(TAG, "Operação já foi cancelada, ignorando resposta tardia");
                    return;
                }

                if (success) {
                    Log.d(TAG, "Banco de dados inicializado com sucesso");
                    retryCount.set(0); // Resetar contador em caso de sucesso

                    // Registra o listener de alteração de dados após inicialização bem-sucedida
                    DatabaseManager.addDataChangeListener(this);

                    carregarServicos();
                } else {
                    handleDatabaseInitError(message);
                }
            });
        } else {
            Log.d(TAG, "Banco de dados já inicializado. Carregando serviços...");
            cancelTimeout(); // Não precisamos do timeout neste caso

            // Registra o listener de alteração de dados se o banco já estiver inicializado
            DatabaseManager.addDataChangeListener(this);

            carregarServicos();
        }
    }

    private void handleDatabaseInitError(String message) {
        Log.e(TAG, "Falha na inicialização do banco: " + message);

        if (isNetworkRelatedError(message) && retryCount.get() < MAX_RETRY_ATTEMPTS) {
            // Erro de rede, tentar novamente após um pequeno delay
            int currentRetry = retryCount.incrementAndGet();

            runOnUiThread(() -> {
                Toast.makeText(Servicos.this,
                        "Tentativa " + currentRetry + " de " + MAX_RETRY_ATTEMPTS,
                        Toast.LENGTH_SHORT).show();

                // Aguarda um pouco antes de tentar novamente
                timeoutHandler.postDelayed(() -> {
                    if (!operationCancelled.get()) {
                        Log.d(TAG, "Tentando reconectar ao banco de dados (tentativa " + currentRetry + ")");
                        initializeDatabase();
                    }
                }, RETRY_DELAY_MS * currentRetry);
            });
        } else {
            // Se não for erro de rede ou já tentamos muitas vezes
            runOnUiThread(() -> {
                showLoading(false);

                if (isCriticalError(message)) {
                    mostrarErroFatal("Problema crítico com o banco de dados", message);
                } else {
                    mostrarErroConexao("Não foi possível conectar ao banco de dados", null);
                }
            });
        }
    }

    // Inicia o timer de timeout
    private void startConnectionTimeout() {
        cancelTimeout(); // Cancela qualquer timeout existente

        timeoutRunnable = () -> {
            if (!operationCancelled.getAndSet(true)) {
                Log.e(TAG, "Timeout na conexão com o banco de dados");

                runOnUiThread(() -> {
                    showLoading(false);
                    mostrarErroTimeout();
                });
            }
        };

        timeoutHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT_MS);
    }

    // Cancela o timer de timeout
    private void cancelTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    // Cancela todas as operações pendentes
    public void cancelOperations() {
        operationCancelled.set(true);
        cancelTimeout();

        if (executor != null && !executor.isShutdown()) {
            // Poderia usar executor.shutdownNow() para interromper tarefas,
            // mas isso pode causar outros problemas. Vamos apenas sinalizar para
            // os callbacks ignorarem suas respostas.
            Log.d(TAG, "Operações canceladas pelo usuário");
        }
    }

    // Métodos para identificar tipos de erros
    private boolean isNetworkRelatedError(String errorMessage) {
        if (errorMessage == null) return false;

        String lowerCaseError = errorMessage.toLowerCase();
        return lowerCaseError.contains("network") ||
                lowerCaseError.contains("conexão") ||
                lowerCaseError.contains("timeout") ||
                lowerCaseError.contains("socket") ||
                lowerCaseError.contains("host") ||
                lowerCaseError.contains("unreachable") ||
                lowerCaseError.contains("inalcançável");
    }

    private boolean isCriticalError(String errorMessage) {
        if (errorMessage == null) return false;

        String lowerCaseError = errorMessage.toLowerCase();
        return lowerCaseError.contains("corrup") ||
                lowerCaseError.contains("permissão") ||
                lowerCaseError.contains("permission") ||
                lowerCaseError.contains("schema") ||
                lowerCaseError.contains("critical") ||
                lowerCaseError.contains("crítico");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Registra o listener de alteração de dados quando a tela volta a ficar visível
        if (DatabaseManager.isInitialized()) {
            DatabaseManager.addDataChangeListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Remove o listener de alteração de dados quando a tela não está visível
        DatabaseManager.removeDataChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        // Cancela todas as operações pendentes
        cancelOperations();

        // Remove os listeners para evitar vazamentos de memória
        DatabaseManager.removeConnectionStateListener(this);
        DatabaseManager.removeDataChangeListener(this);

        // Desliga o executor para liberar recursos
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        super.onDestroy();
    }

    private void carregarServicos() {
        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "Tentativa de carregar serviços com banco não inicializado");
            runOnUiThread(() -> {
                showLoading(false);
                mostrarErroNaInterface("Banco de dados indisponível");
            });
            return;
        }

        // Evita múltiplas chamadas simultâneas
        if (isLoading) return;
        isLoading = true;

        // Resetar flag de cancelamento
        operationCancelled.set(false);
        showLoading(true);

        // Definir timeout para carregamento
        startConnectionTimeout();

        executor.execute(() -> {
            ServicoDAO.listarServicosAsync((servicos, success, message) -> {
                // Cancelar o timeout pois recebemos uma resposta
                cancelTimeout();

                isLoading = false;

                if (operationCancelled.get()) {
                    Log.d(TAG, "Operação cancelada, ignorando resposta de listagem");
                    return;
                }

                runOnUiThread(() -> {
                    showLoading(false);
                    if (success && servicos != null) {
                        atualizarListaServicos(servicos);
                    } else {
                        Log.e(TAG, "Erro ao carregar serviços: " + message);
                        mostrarErroNaInterface("Não foi possível carregar os serviços");

                        // Mostra mais detalhes na snackbar (para o usuário poder relatar o problema)
                        Snackbar.make(recyclerServicos,
                                        "Falha ao carregar dados",
                                        Snackbar.LENGTH_LONG)
                                .setAction("Tentar Novamente", v -> carregarServicos())
                                .show();
                    }
                });
            });
        });
    }

    private void atualizarListaServicos(List<Servico> servicos) {
        listaServicos.clear();
        listaServicos.addAll(servicos);
        adapter.updateList(servicos);

        // Atualiza a visibilidade dos componentes
        recyclerServicos.setVisibility(servicos.isEmpty() ? View.GONE : View.VISIBLE);
        txtEmpty.setVisibility(servicos.isEmpty() ? View.VISIBLE : View.GONE);
        txtEmpty.setText(servicos.isEmpty() ? "Nenhum serviço cadastrado" : "");
    }

    private void showLoading(boolean show) {
        if (progressBarServicos != null) {
            progressBarServicos.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void mostrarErroNaInterface(String mensagem) {
        if (txtEmpty != null) {
            txtEmpty.setText(mensagem);
            txtEmpty.setVisibility(View.VISIBLE);
        }

        if (recyclerServicos != null) {
            recyclerServicos.setVisibility(View.GONE);
        }

        Toast.makeText(this, mensagem, Toast.LENGTH_SHORT).show();
    }

    private void mostrarErroConexao(String mensagemUsuario, String detalhesErro) {
        mostrarErroNaInterface(mensagemUsuario != null ? mensagemUsuario : "Problema de conexão");

        // Log do erro técnico (apenas para depuração)
        if (detalhesErro != null) {
            Log.e(TAG, "Erro de conexão: " + detalhesErro);
        }

        // Oferece opção de tentar novamente
        Snackbar.make(recyclerServicos,
                        "Problema de conexão",
                        Snackbar.LENGTH_LONG)
                .setAction("Tentar Novamente", v -> initializeDatabase())
                .show();
    }

    private void mostrarErroTimeout() {
        mostrarErroNaInterface("A conexão demorou muito para responder");

        Snackbar.make(recyclerServicos,
                        "Tempo limite de conexão excedido",
                        Snackbar.LENGTH_LONG)
                .setAction("Tentar Novamente", v -> initializeDatabase())
                .show();
    }

    private void mostrarErroFatal(String mensagemUsuario, String detalhesErro) {
        // Log do erro técnico detalhado
        Log.e(TAG, "Erro fatal: " + detalhesErro);

        // Mostra um diálogo com opção de fechar o app
        new AlertDialog.Builder(this)
                .setTitle("Erro crítico")
                .setMessage(mensagemUsuario)
                .setPositiveButton("Tentar novamente", (dialog, which) -> {
                    retryCount.set(0);
                    initializeDatabase();
                })
                .setNegativeButton("Fechar aplicativo", (dialog, which) -> {
                    finishAffinity(); // Fecha todas as activities do app
                })
                .setCancelable(false)
                .show();
    }

    private void abrirFormularioEdicao(Servico servico) {
        Intent intent = new Intent(Servicos.this, FormServicoActivity.class);
        intent.putExtra("servico", servico);
        formServicoLauncher.launch(intent);
    }

    private void confirmarExclusao(Servico servico) {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar exclusão")
                .setMessage("Tem certeza que deseja excluir o serviço '" + servico.getNome() + "'?")
                .setPositiveButton("Sim", (dialog, which) -> excluirServico(servico))
                .setNegativeButton("Não", null)
                .show();
    }

    private void excluirServico(Servico servico) {
        showLoading(true);

        executor.execute(() -> {
            ServicoDAO.excluirServicoAsync(servico.getId_servico(), (success, message, data) -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    if (success) {
                        carregarServicos(); // Recarrega a lista após exclusão
                        Toast.makeText(Servicos.this, "Serviço excluído com sucesso", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(Servicos.this, "Erro ao excluir serviço: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Intent intent = null;

        if (id == R.id.nav_servico) {
            // Já estamos na tela de serviços, não faz nada
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        } else if (id == R.id.nav_vendas) {
            intent = new Intent(this, Vendas.class);
        } else if (id == R.id.nav_estoque) {
            intent = new Intent(this, EstoqueActivity.class);
        } else if (id == R.id.nav_home) {
            intent = new Intent(this, Home.class);
        } else if (id == R.id.nav_relatorio) {
            intent = new Intent(this, Relatorios.class);
        }

        if (intent != null) {
            // Limpa a stack de activities para evitar acumular memória
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }

        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    @Override
    public void onConnectionStateChanged(boolean isConnected, String message) {
        runOnUiThread(() -> {
            if (isConnected) {
                Log.d(TAG, "Conexão com o banco de dados restabelecida");
                carregarServicos();
            } else {
                Log.e(TAG, "Conexão com o banco perdida: " + message);
                mostrarErroConexao("Conexão com o banco de dados perdida", message);
            }
        });
    }

    // Implementação do método da interface DataChangeListener
    @Override
    public void onDataChanged() {
        Log.d(TAG, "Notificação de alteração de dados recebida, recarregando serviços");

        // Recarrega os serviços quando houver alterações no banco de dados
        if (!isLoading) {
            carregarServicos();
        }
    }
}
