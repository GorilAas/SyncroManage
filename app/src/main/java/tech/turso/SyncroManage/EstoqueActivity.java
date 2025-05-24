package tech.turso.SyncroManage;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EstoqueActivity extends BaseActivity implements DatabaseManager.DataChangeListener {
    private static final String TAG = "EstoqueActivity";
    private static final int MAX_RETRIES = 3;
    private static final long TIMEOUT_MILLIS = 30000; // 30 segundos

    private RecyclerView recyclerViewEstoque;
    private EstoqueAdapter adapter;
    private List<Estoque> listaEstoque;
    private LinearLayout layoutEstoqueVazio;
    private ProgressBar progressBarEstoque;
    private SearchView searchView;
    private FloatingActionButton fabAdicionarProduto;
    private ExecutorService executor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NumberFormat formatoMoeda;
    private int retryCount = 0;
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    private final ActivityResultLauncher<Intent> formEstoqueLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    carregarEstoque();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_estoque);

        // Inicializa o executor
        executor = Executors.newSingleThreadExecutor();

        formatoMoeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        setupNavigationDrawer(R.id.drawer_layout, R.id.toolbar, R.id.nav_view, R.id.nav_estoque);

        inicializarViews();
        configurarRecyclerView();
        configurarSearchView();
        configurarFabAdicionar();

        // Inicializando banco de dados se necessário
        inicializarBancoDados();
    }

    private void inicializarViews() {
        recyclerViewEstoque = findViewById(R.id.recyclerViewEstoque);
        layoutEstoqueVazio = findViewById(R.id.layoutEstoqueVazio);
        progressBarEstoque = findViewById(R.id.progressBarEstoque);
        searchView = findViewById(R.id.searchView);
        fabAdicionarProduto = findViewById(R.id.fabAdicionarProduto);
    }

    private void configurarRecyclerView() {
        listaEstoque = new ArrayList<>();
        adapter = new EstoqueAdapter(this, listaEstoque, new EstoqueAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Estoque item) { /* Opcional */ }

            @Override
            public void onEditClick(Estoque item) {
                abrirFormularioEdicao(item);
            }

            @Override
            public void onAjustQuantidadeClick(Estoque item) {
                mostrarDialogoAjusteQuantidade(item);
            }

            @Override
            public void onDeleteClick(Estoque item) {
                mostrarDialogoConfirmacaoExclusao(item);
            }
        });

        recyclerViewEstoque.setLayoutManager(new LinearLayoutManager(EstoqueActivity.this));
        recyclerViewEstoque.setHasFixedSize(true); // Otimização se todos os itens têm o mesmo tamanho
        recyclerViewEstoque.setAdapter(adapter);
    }

    private void configurarSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Implementando delay para não filtrar a cada caractere digitado
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> adapter.getFilter().filter(newText), 300);
                return false;
            }
        });
    }

    private void configurarFabAdicionar() {
        fabAdicionarProduto.setOnClickListener(v -> {
            Intent intent = new Intent(EstoqueActivity.this, FormEstoqueActivity.class);
            formEstoqueLauncher.launch(intent);
        });
    }

    private void abrirFormularioEdicao(Estoque item) {
        Intent intent = new Intent(EstoqueActivity.this, FormEstoqueActivity.class);
        intent.putExtra("item_estoque", (Serializable) item);
        formEstoqueLauncher.launch(intent);
    }

    private void inicializarBancoDados() {
        // Verifica se o banco já está inicializado antes de tentar inicializar
        if (!DatabaseManager.isInitialized()) {
            mostrarLoadingEstoque(true);
            Log.d(TAG, "Iniciando inicialização do banco de dados");

            // Configura o timeout
            timeoutRunnable = () -> {
                Log.e(TAG, "Timeout na inicialização do banco de dados");
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    Log.w(TAG, "Tentando novamente (" + retryCount + "/" + MAX_RETRIES + ")");
                    // Cancela qualquer inicialização em andamento
                    DatabaseManager.cancelInitialization();
                    // Tenta novamente
                    inicializarBancoDados();
                } else {
                    Log.e(TAG, "Número máximo de tentativas excedido");
                    mostrarLoadingEstoque(false);
                    mostrarMensagemErro("Não foi possível conectar ao banco de dados. Por favor, verifique sua conexão e reinicie o aplicativo.");
                    exibirDialogoErroFatal("Não foi possível inicializar o banco de dados após várias tentativas.");
                }
            };

            // Inicia o timer de timeout
            timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MILLIS);

            DatabaseManager.initializeAsync(this, (success, message) -> {
                // Cancela o timeout pois recebemos uma resposta
                timeoutHandler.removeCallbacks(timeoutRunnable);

                if (success) {
                    Log.i(TAG, "Banco de dados inicializado com sucesso");
                    // Reinicia o contador de tentativas
                    retryCount = 0;

                    // Registra o listener após inicialização bem-sucedida
                    DatabaseManager.addDataChangeListener(this);

                    carregarEstoque();
                } else {
                    Log.e(TAG, "Falha na inicialização do banco: " + message);

                    if (retryCount < MAX_RETRIES && isNetworkRelatedError(message)) {
                        // Tenta novamente automaticamente para erros de rede
                        retryCount++;
                        Log.w(TAG, "Tentando novamente (" + retryCount + "/" + MAX_RETRIES + ")");

                        // Pequeno delay antes de tentar novamente
                        new Handler().postDelayed(() -> {
                            DatabaseManager.initializeAsync(EstoqueActivity.this, this::handleInitializationCallback);
                        }, 1000);
                    } else {
                        mostrarLoadingEstoque(false);
                        mostrarMensagemErro("Não foi possível inicializar o banco de dados. Por favor, reinicie o aplicativo.");

                        // Em caso de erro crítico, apresenta opção de fechar o app
                        if (isCriticalError(message)) {
                            exibirDialogoErroFatal("Ocorreu um erro na inicialização do banco de dados.");
                        }
                    }
                }
            });
        } else {
            Log.d(TAG, "Banco já inicializado, carregando dados");

            // Registra o listener se o banco já estiver inicializado
            DatabaseManager.addDataChangeListener(this);

            carregarEstoque();
        }
    }

    // Método auxiliar para processar o callback de inicialização
    private void handleInitializationCallback(boolean success, String message) {
        if (success) {
            Log.i(TAG, "Banco de dados inicializado com sucesso na tentativa " + retryCount);
            retryCount = 0;

            // Registra o listener após inicialização bem-sucedida
            DatabaseManager.addDataChangeListener(this);

            carregarEstoque();
        } else {
            Log.e(TAG, "Falha na inicialização do banco na tentativa " + retryCount + ": " + message);
            mostrarLoadingEstoque(false);
            mostrarMensagemErro("Não foi possível inicializar o banco de dados. Por favor, reinicie o aplicativo.");
        }
    }

    // Método para exibir diálogo de erro fatal com opção de fechar o app
    private void exibirDialogoErroFatal(String mensagem) {
        new AlertDialog.Builder(this)
                .setTitle("Erro no Sistema")
                .setMessage(mensagem + " É recomendável fechar o aplicativo. Deseja fechar agora?")
                .setPositiveButton("Sim", (dialog, which) -> finishAffinity())
                .setNegativeButton("Não", null)
                .setCancelable(false)
                .show();
    }

    // Método para identificar se o erro está relacionado à rede
    private boolean isNetworkRelatedError(String errorMessage) {
        if (errorMessage == null) return false;

        return errorMessage.toLowerCase().contains("timeout") ||
                errorMessage.toLowerCase().contains("connection") ||
                errorMessage.toLowerCase().contains("network") ||
                errorMessage.toLowerCase().contains("internet");
    }

    // Método para identificar erros críticos que impossibilitam o funcionamento do app
    private boolean isCriticalError(String errorMessage) {
        if (errorMessage == null) return false;

        return errorMessage.toLowerCase().contains("corrupt") ||
                errorMessage.toLowerCase().contains("permission") ||
                errorMessage.toLowerCase().contains("cannot access") ||
                errorMessage.toLowerCase().contains("schema") ||
                errorMessage.toLowerCase().contains("version") ||
                errorMessage.toLowerCase().contains("incompatible");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (navigationView != null) {
            navigationView.setCheckedItem(R.id.nav_estoque);
        }

        // Só recarrega os dados se o banco já estiver inicializado
        if (DatabaseManager.isInitialized()) {
            // Registra o listener ao retornar à tela
            DatabaseManager.addDataChangeListener(this);

            carregarEstoque();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Remove o listener quando a tela não está visível
        DatabaseManager.removeDataChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove o listener para evitar vazamento de memória
        DatabaseManager.removeDataChangeListener(this);

        // Cancelar qualquer timeout pendente para evitar vazamento de memória
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        // Shutdown do executor para evitar vazamento de memória
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // Implementação do método da interface DataChangeListener
    @Override
    public void onDataChanged() {
        Log.d(TAG, "Notificação de alteração de dados recebida, recarregando estoque");
        carregarEstoque();
    }

    private void mostrarLoadingEstoque(boolean mostrar) {
        runOnUiThread(() -> {
            progressBarEstoque.setVisibility(mostrar ? View.VISIBLE : View.GONE);
            recyclerViewEstoque.setVisibility(mostrar ? View.GONE :
                    (listaEstoque.isEmpty() ? View.GONE : View.VISIBLE));
            layoutEstoqueVazio.setVisibility(mostrar ? View.GONE :
                    (listaEstoque.isEmpty() ? View.VISIBLE : View.GONE));
        });
    }

    private void carregarEstoque() {
        mostrarLoadingEstoque(true);

        executor.execute(() -> {
            try {
                List<Estoque> resultado = EstoqueDAO.listarEstoque();

                handler.post(() -> {
                    // Usando DiffUtil através do adapter para atualização eficiente
                    adapter.atualizarDados(resultado != null ? resultado : new ArrayList<>());

                    // Atualizando lista local
                    listaEstoque.clear();
                    if (resultado != null) listaEstoque.addAll(resultado);

                    mostrarLoadingEstoque(false);
                    verificarEstoqueVazio();
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar estoque", e);
                handler.post(() -> {
                    mostrarLoadingEstoque(false);
                    mostrarMensagemErro("Não foi possível carregar os produtos. Tente novamente.");
                });
            }
        });
    }

    private void verificarEstoqueVazio() {
        boolean estoqueVazio = listaEstoque == null || listaEstoque.isEmpty();
        layoutEstoqueVazio.setVisibility(estoqueVazio ? View.VISIBLE : View.GONE);
        recyclerViewEstoque.setVisibility(estoqueVazio ? View.GONE : View.VISIBLE);
    }

    private void mostrarDialogoConfirmacaoExclusao(Estoque item) {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar Exclusão")
                .setMessage("Tem certeza que deseja excluir o item '" + item.getNome_produto() + "'?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Excluir", (dialog, which) -> excluirItemEstoque(item))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void excluirItemEstoque(Estoque item) {
        mostrarLoadingEstoque(true);

        executor.execute(() -> {
            try {
                boolean sucesso = EstoqueDAO.excluirItemEstoque(item.getId_estoque());

                handler.post(() -> {
                    mostrarLoadingEstoque(false);
                    if (sucesso) {
                        // Remove eficientemente apenas o item excluído
                        adapter.removeItem(item);
                        listaEstoque.remove(item);
                        verificarEstoqueVazio();

                        Toast.makeText(EstoqueActivity.this, "Item excluído com sucesso!", Toast.LENGTH_SHORT).show();
                    } else {
                        mostrarMensagemErro("Não foi possível excluir o item. Tente novamente.");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao excluir item", e);
                handler.post(() -> {
                    mostrarLoadingEstoque(false);
                    mostrarMensagemErro("Ocorreu um erro ao excluir o item.");
                });
            }
        });
    }

    private void mostrarDialogoAjusteQuantidade(Estoque item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ajustar Quantidade");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_ajustar_quantidade, null);
        EditText inputQuantidade = viewInflated.findViewById(R.id.editNovaQuantidade);

        TextView textQuantidadeAtual = viewInflated.findViewById(R.id.textQuantidadeAtual);
        textQuantidadeAtual.setText(String.valueOf(item.getQuantidade()));

        // Definir o valor atual como valor inicial do campo
        inputQuantidade.setText(String.valueOf(item.getQuantidade()));
        inputQuantidade.selectAll();

        builder.setView(viewInflated);
        builder.setPositiveButton("Confirmar", (dialog, which) -> {
            String quantidadeStr = inputQuantidade.getText().toString();
            if (!quantidadeStr.isEmpty()) {
                try {
                    int novaQuantidade = Integer.parseInt(quantidadeStr);
                    if (novaQuantidade >= 0) {
                        ajustarQuantidade(item, novaQuantidade);
                    } else {
                        mostrarMensagemErro("A quantidade não pode ser negativa");
                    }
                } catch (NumberFormatException e) {
                    mostrarMensagemErro("Quantidade inválida");
                }
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void ajustarQuantidade(Estoque item, int novaQuantidade) {
        if (item.getQuantidade() == novaQuantidade) {
            // Nenhuma alteração necessária
            return;
        }

        mostrarLoadingEstoque(true);

        executor.execute(() -> {
            try {
                // Cria uma cópia do item com a nova quantidade
                Estoque itemAtualizado = new Estoque(
                        item.getId_estoque(),
                        item.getId_usuario(),
                        item.getNome_produto(),
                        item.getCusto_unitario(),
                        item.getValor_unitario(),
                        novaQuantidade
                );

                boolean sucesso = EstoqueDAO.atualizarItemEstoque(itemAtualizado);

                handler.post(() -> {
                    mostrarLoadingEstoque(false);
                    if (sucesso) {
                        // Atualiza o item na lista local
                        item.setQuantidade(novaQuantidade);
                        adapter.notifyItemChanged(listaEstoque.indexOf(item));

                        Toast.makeText(EstoqueActivity.this, "Quantidade atualizada com sucesso!", Toast.LENGTH_SHORT).show();
                    } else {
                        mostrarMensagemErro("Não foi possível atualizar a quantidade. Tente novamente.");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao ajustar quantidade", e);
                handler.post(() -> {
                    mostrarLoadingEstoque(false);
                    mostrarMensagemErro("Ocorreu um erro ao atualizar a quantidade.");
                });
            }
        });
    }

    private void mostrarMensagemErro(String mensagem) {
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Implementação do método da interface NavigationView.OnNavigationItemSelectedListener
        int id = item.getItemId();

        if (id == R.id.nav_estoque) {
            // Já estamos na tela de estoque, apenas fecha o drawer
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        }

        // Delega para a implementação da classe pai
        return super.onNavigationItemSelected(item);
    }
}
