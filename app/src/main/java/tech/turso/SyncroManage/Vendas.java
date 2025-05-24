package tech.turso.SyncroManage;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Vendas extends BaseActivity implements VendaAdapter.OnVendaClickListener {

    private static final String TAG = "VendasActivity";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long TIMEOUT_DURATION = 30000; // 30 segundos
    private static final long RETRY_DELAY = 2000; // 2 segundos

    private RecyclerView recyclerViewVendas;
    private VendaAdapter vendaAdapter;
    private List<Venda> listaDeVendas = new ArrayList<>();
    private FloatingActionButton fabAdicionarVenda;
    private SearchView searchViewVendas;
    private ProgressBar progressBar;
    private View emptyStateLayout;

    // Utiliza um executor de thread única para operações de banco de dados
    private ExecutorService executorService;
    private Handler mainHandler;

    // Handler para timeout e retry
    private Runnable timeoutRunnable;
    private int retryCount = 0;

    // Flag para evitar múltiplas solicitações simultâneas
    private AtomicBoolean isLoading = new AtomicBoolean(false);
    private AtomicBoolean databaseInitialized = new AtomicBoolean(false);

    private ActivityResultLauncher<Intent> formVendaLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendas);

        initExecutor();
        bindViews();
        setupNavigationDrawer(R.id.drawer_layout, R.id.toolbar, R.id.nav_view, R.id.nav_vendas);
        setupRecyclerView();
        setupSearchView();
        setupFab();
        setupActivityResultLaunchers();

        inicializarBancoDeDados();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Se já estiver inicializado e não estiver carregando, recarregar as vendas
        if (DatabaseManager.isInitialized() && !isLoading.get()) {
            carregarVendas();
        }
    }

    private void initExecutor() {
        // Inicializa um executor de thread única para operações de banco de dados
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    private void bindViews() {
        recyclerViewVendas = findViewById(R.id.recycler_vendas);
        fabAdicionarVenda = findViewById(R.id.fab_adicionar_venda);
        searchViewVendas = findViewById(R.id.search_view_vendas);
        progressBar = findViewById(R.id.progress_bar);
        emptyStateLayout = findViewById(R.id.empty_state);
    }

    private void setupRecyclerView() {
        // Configuração otimizada do RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewVendas.setLayoutManager(layoutManager);
        recyclerViewVendas.setHasFixedSize(true);

        // Desativa animações padrão para melhor desempenho
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        recyclerViewVendas.setItemAnimator(itemAnimator);

        // Inicializa o adapter com a lista vazia
        vendaAdapter = new VendaAdapter(this, new ArrayList<>());
        vendaAdapter.setOnVendaClickListener(this);
        recyclerViewVendas.setAdapter(vendaAdapter);
    }

    private void setupSearchView() {
        searchViewVendas.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (vendaAdapter != null) {
                    vendaAdapter.getFilter().filter(newText);
                }
                return true;
            }
        });

        // Otimização: Definir hint específico para melhorar UX
        searchViewVendas.setQueryHint("Buscar por nome, data ou pagamento");
    }

    private void setupFab() {
        fabAdicionarVenda.setOnClickListener(view -> abrirFormularioNovaVenda());
    }

    private void setupActivityResultLaunchers() {
        formVendaLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        carregarVendas();
                    }
                });
    }

    private void inicializarBancoDeDados() {
        // Evitar inicialização múltipla
        if (isLoading.getAndSet(true)) {
            Log.d(TAG, "Uma operação de inicialização já está em andamento");
            return;
        }

        // Mostra o progresso antes de iniciar o carregamento
        mostrarProgresso(true);
        retryCount = 0;

        // Configura o timeout para a operação
        configurarTimeout();

        // Verifica se o banco já está inicializado antes de tentar inicializar
        if (!DatabaseManager.isInitialized()) {
            Log.d(TAG, "Inicializando banco de dados...");
            inicializarBancoDeDadosComRetry();
        } else {
            Log.d(TAG, "Banco já inicializado, carregando vendas.");
            databaseInitialized.set(true);
            cancelarTimeout(); // Cancela o timeout pois não será necessário
            carregarVendas();
        }
    }

    private void inicializarBancoDeDadosComRetry() {
        if (!isActivityActive()) {
            isLoading.set(false);
            return;
        }

        DatabaseManager.initializeAsync(this, (success, message) -> {
            // Cancela o timeout assim que recebemos uma resposta
            cancelarTimeout();

            if (success) {
                Log.i(TAG, "Banco de dados inicializado com sucesso.");
                databaseInitialized.set(true);
                retryCount = 0; // Reseta o contador de tentativas
                carregarVendas();
            } else {
                Log.e(TAG, "Falha na inicialização do banco: " + message);

                // Verifica se deve tentar novamente com base no tipo de erro
                if (retryCount < MAX_RETRY_ATTEMPTS && isNetworkError(message)) {
                    retryCount++;
                    Log.w(TAG, "Tentando novamente... Tentativa " + retryCount + " de " + MAX_RETRY_ATTEMPTS);

                    mainHandler.postDelayed(() -> {
                        if (isActivityActive()) {
                            Log.d(TAG, "Executando retry após delay...");
                            // Não definir isLoading para false aqui, pois ainda estamos tentando
                            inicializarBancoDeDadosComRetry();
                        } else {
                            isLoading.set(false);
                        }
                    }, RETRY_DELAY);
                } else {
                    isLoading.set(false);
                    mostrarProgresso(false);

                    if (isActivityActive()) {
                        if (isCriticalError(message)) {
                            mostrarDialogoErroCritico(message);
                        } else {
                            mostrarErro("Não foi possível inicializar o banco de dados. Tente novamente mais tarde.");
                        }
                    }
                }
            }
        });
    }

    private void configurarTimeout() {
        cancelarTimeout(); // Cancela qualquer timeout existente

        timeoutRunnable = () -> {
            Log.e(TAG, "Timeout ao inicializar banco de dados");

            if (isActivityActive()) {
                // Verifica se deve tentar novamente
                if (retryCount < MAX_RETRY_ATTEMPTS) {
                    retryCount++;
                    Log.w(TAG, "Timeout ocorreu. Tentando novamente... Tentativa " + retryCount + " de " + MAX_RETRY_ATTEMPTS);
                    mainHandler.postDelayed(() -> {
                        if (isActivityActive()) {
                            cancelarOperacoes();
                            inicializarBancoDeDadosComRetry();
                        } else {
                            isLoading.set(false);
                        }
                    }, RETRY_DELAY);
                } else {
                    // Libera o estado de carregamento
                    isLoading.set(false);
                    mostrarProgresso(false);

                    // Exibir mensagem de erro após exceder o número máximo de tentativas
                    mostrarDialogoErroCritico("O aplicativo não conseguiu se conectar ao banco de dados após várias tentativas.");
                }
            } else {
                isLoading.set(false);
            }
        };

        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_DURATION);
    }

    private void cancelarTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void cancelarOperacoes() {
        // Este método deve cancelar quaisquer operações em andamento
        Log.d(TAG, "Cancelando operações em andamento...");
        // Como não há implementação específica no DatabaseManager para cancelar operações,
        // apenas registramos a intenção
    }

    private boolean isNetworkError(String errorMessage) {
        // Verifica se a mensagem de erro está relacionada a problemas de rede
        if (errorMessage == null) return false;

        return errorMessage.toLowerCase().contains("network") ||
                errorMessage.toLowerCase().contains("connection") ||
                errorMessage.toLowerCase().contains("timeout") ||
                errorMessage.toLowerCase().contains("internet") ||
                errorMessage.toLowerCase().contains("socket");
    }

    private boolean isCriticalError(String errorMessage) {
        // Identifica erros críticos que podem requerer intervenção do usuário
        if (errorMessage == null) return false;

        return errorMessage.toLowerCase().contains("corruption") ||
                errorMessage.toLowerCase().contains("permission") ||
                errorMessage.toLowerCase().contains("disk full") ||
                errorMessage.toLowerCase().contains("access denied") ||
                errorMessage.toLowerCase().contains("locked");
    }

    private void mostrarDialogoErroCritico(String message) {
        if (!isActivityActive()) {
            isLoading.set(false);
            return;
        }

        Log.e(TAG, "Erro crítico: " + message);

        new AlertDialog.Builder(this)
                .setTitle("Erro Grave")
                .setMessage("Ocorreu um problema ao inicializar o aplicativo. O que deseja fazer?")
                .setPositiveButton("Tentar novamente", (dialog, which) -> {
                    retryCount = 0;
                    isLoading.set(false); // Permite nova tentativa
                    inicializarBancoDeDados();
                })
                .setNegativeButton("Fechar aplicativo", (dialog, which) -> finishAffinity())
                .setCancelable(false)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void carregarVendas() {
        // Evita múltiplas chamadas simultâneas
        if (!isActivityActive() || !databaseInitialized.get()) {
            isLoading.set(false);
            return;
        }

        // Se já estiver carregando, não inicia outra operação
        if (!isLoading.compareAndSet(false, true)) {
            Log.d(TAG, "Já existe um carregamento de vendas em andamento");
            return;
        }

        mostrarProgresso(true);
        Log.d(TAG, "Iniciando carregamento de vendas");

        VendaDAO.listarVendasAsync((result, success, message) -> {
            // Garante que o callback é processado na thread principal
            mainHandler.post(() -> {
                try {
                    // Verifica se a atividade ainda está ativa
                    if (!isActivityActive()) {
                        Log.d(TAG, "Atividade não está mais ativa, cancelando atualização da UI");
                        return;
                    }

                    Log.d(TAG, "Callback de listarVendasAsync recebido. Success: " + success +
                            ", Resultados: " + (result != null ? result.size() : "null"));

                    mostrarProgresso(false);
                    listaDeVendas.clear();

                    if (success && result != null) {
                        listaDeVendas.addAll(result);
                        atualizarInterface(result.isEmpty());

                        if (result.isEmpty()) {
                            Log.i(TAG, "Nenhuma venda encontrada");
                        } else {
                            Log.i(TAG, "Vendas carregadas: " + result.size());
                        }
                    } else {
                        atualizarInterface(true);

                        if (!success) {
                            Log.w(TAG, "Erro ao carregar vendas: " + message);
                            mostrarErro("Não foi possível carregar as vendas. Tente novamente.");
                        }
                    }
                } finally {
                    // Sempre libera o flag de carregamento
                    isLoading.set(false);
                }
            });
        });
    }

    private void atualizarInterface(boolean mostrarEstadoVazio) {
        // Prote contra chamadas inválidas fora do ciclo de vida
        if (!isActivityActive()) return;

        recyclerViewVendas.setVisibility(mostrarEstadoVazio ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(mostrarEstadoVazio ? View.VISIBLE : View.GONE);

        if (!mostrarEstadoVazio && vendaAdapter != null) {
            vendaAdapter.updateList(new ArrayList<>(listaDeVendas));
        }
    }

    private void mostrarProgresso(boolean mostrar) {
        if (!isActivityActive()) return;

        if (progressBar != null) {
            progressBar.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        }

        if (mostrar) {
            if (recyclerViewVendas != null) recyclerViewVendas.setVisibility(View.GONE);
            if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void abrirFormularioNovaVenda() {
        // Verifica se pode abrir o formulário
        if (!isActivityActive() || !DatabaseManager.isInitialized()) {
            mostrarErro("Aguarde a inicialização do aplicativo");
            return;
        }

        Intent intent = new Intent(Vendas.this, FormVendaActivity.class);
        formVendaLauncher.launch(intent);
    }

    private void abrirFormularioEdicaoVenda(Venda venda) {
        // Verifica se pode abrir o formulário
        if (!isActivityActive() || !DatabaseManager.isInitialized()) {
            mostrarErro("Aguarde a inicialização do aplicativo");
            return;
        }

        Intent intent = new Intent(Vendas.this, FormVendaActivity.class);
        intent.putExtra(FormVendaActivity.EXTRA_VENDA_EDITAR, venda);
        formVendaLauncher.launch(intent);
    }

    @Override
    public void onVendaClick(Venda venda, View view) {
        // Implementação futura se necessário
    }

    @Override
    public void onVendaEdit(Venda venda) {
        abrirFormularioEdicaoVenda(venda);
    }

    @Override
    public void onVendaDelete(int idVenda) {
        if (!isActivityActive() || !DatabaseManager.isInitialized()) {
            mostrarErro("Aguarde a inicialização do aplicativo");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Excluir Venda")
                .setMessage("Tem certeza que deseja excluir esta venda? Esta ação não pode ser desfeita.")
                .setPositiveButton("Excluir", (dialog, which) -> excluirVenda(idVenda))
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void excluirVenda(int idVenda) {
        // Verifica se a atividade ainda está ativa
        if (!isActivityActive() || isLoading.getAndSet(true)) {
            return;
        }

        mostrarProgresso(true);
        Log.d(TAG, "Excluindo venda ID: " + idVenda);

        VendaDAO.excluirVendaAsync(idVenda, (result, success, message) -> {
            // Garante que o callback é processado na thread principal
            mainHandler.post(() -> {
                try {
                    // Verifica novamente se a atividade está ativa
                    if (!isActivityActive()) return;

                    mostrarProgresso(false);

                    if (success && result != null && result) {
                        mostrarMensagem("Venda excluída com sucesso");
                        removerVendaDaLista(idVenda);
                    } else {
                        String erroLog = message != null && !message.isEmpty() ?
                                message : "Erro desconhecido ao excluir venda";
                        Log.e(TAG, "Erro ao excluir venda (ID: " + idVenda + "): " + erroLog);
                        // Mensagem simplificada para o usuário
                        mostrarErro("Não foi possível excluir a venda.");
                    }
                } finally {
                    // Sempre libera o flag de carregamento
                    isLoading.set(false);
                }
            });
        });
    }

    private void removerVendaDaLista(int idVenda) {
        if (!isActivityActive()) return;

        listaDeVendas.removeIf(venda -> venda.getId_venda() == idVenda);
        if (vendaAdapter != null) {
            vendaAdapter.updateList(new ArrayList<>(listaDeVendas));
        }

        if (listaDeVendas.isEmpty()) {
            if (recyclerViewVendas != null) recyclerViewVendas.setVisibility(View.GONE);
            if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (super.onNavigationItemSelected(item)) {
            return true;
        }

        if (id == R.id.nav_estoque) {
            startActivity(new Intent(this, EstoqueActivity.class));
            finish();
            return true;
        } else if (id == R.id.nav_home) {
            startActivity(new Intent(this, Home.class));
            finish();
            return true;
        } else if (id == R.id.nav_servico) {
            startActivity(new Intent(this, Servicos.class));
            finish();
            return true;
        } else if (id == R.id.nav_relatorio) {
            startActivity(new Intent(this, Relatorios.class));
            finish();
            return true;
        }

        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        return false;
    }

    // Métodos auxiliares para melhorar legibilidade e manutenção

    private void mostrarMensagem(String mensagem) {
        if (isActivityActive()) {
            Toast.makeText(this, mensagem, Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarErro(String mensagem) {
        if (isActivityActive()) {
            Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isActivityActive() {
        return !isFinishing() && !isDestroyed() &&
                getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cancela quaisquer callbacks de timeout pendentes
        cancelarTimeout();

        // Garante que o executor seja desligado para evitar vazamentos de memória
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}