package tech.turso.SyncroManage;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Home extends BaseActivity implements DatabaseManager.DataChangeListener {

    private static final String TAG = "Home";
    private static final int MAX_RETRIES = 3;
    private static final long TIMEOUT_MILLIS = 10000; // 10 segundos
    private static final long AUTH_CHECK_DELAY = 500; // 500ms para verificar autenticação

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView textoResultado;
    private TextView txtVendasHoje, txtVendasMes, txtReceitaHoje, txtReceitaMes;
    private TextView txtSemItensAlta, txtSemEstoqueBaixo;
    private RecyclerView recyclerItensAlta, recyclerEstoqueBaixo;
    private Button btnVerEstoque;
    private View loadingView;

    private ItensAltaAdapter itensAltaAdapter;
    private EstoqueBaixoAdapter estoqueBaixoAdapter;

    private Handler timeoutHandler = new Handler();
    private Runnable timeoutRunnable;
    private int retryCount = 0;

    // Handler para verificação de autenticação
    private Handler authCheckHandler = new Handler();
    private Runnable authCheckRunnable;
    private boolean isAuthCheckRunning = false;

    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar componentes
        inicializarComponentes();

        // Configurar Navigation Drawer
        setupNavigationDrawer(R.id.drawer_layout, R.id.toolbar, R.id.nav_view, R.id.nav_home);

        // Configurar RecyclerViews
        configurarRecyclerViews();

        // Configurar ações
        configurarAcoes();

        // Inicializar banco de dados com verificação de autenticação
        verificarAutenticacaoEInicializarBanco();
    }

    private void inicializarComponentes() {
        textoResultado = findViewById(R.id.textoResultado);
        loadingView = findViewById(R.id.loadingView);

        // Componentes do resumo financeiro
        txtVendasHoje = findViewById(R.id.txtVendasHoje);
        txtVendasMes = findViewById(R.id.txtVendasMes);
        txtReceitaHoje = findViewById(R.id.txtReceitaHoje);
        txtReceitaMes = findViewById(R.id.txtReceitaMes);

        // Mensagens vazias
        txtSemItensAlta = findViewById(R.id.txtSemItensAlta);
        txtSemEstoqueBaixo = findViewById(R.id.txtSemEstoqueBaixo);

        // RecyclerViews
        recyclerItensAlta = findViewById(R.id.recyclerItensAlta);
        recyclerEstoqueBaixo = findViewById(R.id.recyclerEstoqueBaixo);

        // Botões
        btnVerEstoque = findViewById(R.id.btnVerEstoque);
    }

    private void configurarRecyclerViews() {
        // Configurar RecyclerView de itens em alta
        recyclerItensAlta.setLayoutManager(new LinearLayoutManager(this));
        itensAltaAdapter = new ItensAltaAdapter(new ArrayList<>());
        recyclerItensAlta.setAdapter(itensAltaAdapter);

        // Configurar RecyclerView de estoque baixo
        recyclerEstoqueBaixo.setLayoutManager(new LinearLayoutManager(this));
        estoqueBaixoAdapter = new EstoqueBaixoAdapter(new ArrayList<>());
        recyclerEstoqueBaixo.setAdapter(estoqueBaixoAdapter);
    }

    private void configurarAcoes() {
        // Configurar botão Ver Estoque
        btnVerEstoque.setOnClickListener(v -> {
            Intent intent = new Intent(Home.this, EstoqueActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Verifica se o usuário está autenticado antes de inicializar o banco de dados.
     * Usa um mecanismo de polling para aguardar a autenticação completa.
     */
    private void verificarAutenticacaoEInicializarBanco() {
        // Evita múltiplas verificações simultâneas
        if (isAuthCheckRunning) {
            return;
        }

        isAuthCheckRunning = true;
        mostrarLoading(true);
        textoResultado.setText("Verificando autenticação...");

        // Cria um runnable para verificar periodicamente a autenticação
        authCheckRunnable = new Runnable() {
            @Override
            public void run() {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                if (user != null) {
                    // Usuário autenticado, podemos inicializar o banco
                    Log.d(TAG, "Usuário autenticado: " + user.getUid());
                    isAuthCheckRunning = false;
                    textoResultado.setText("Sincronizando dados...");
                    inicializarBancoDados();
                } else {
                    // Usuário ainda não autenticado, verificar novamente após delay
                    Log.d(TAG, "Aguardando autenticação do usuário...");
                    authCheckHandler.postDelayed(this, AUTH_CHECK_DELAY);
                }
            }
        };

        // Inicia a verificação
        authCheckHandler.post(authCheckRunnable);
    }

    private void inicializarBancoDados() {
        // Verifica se o banco já está inicializado antes de tentar inicializar
        if (!DatabaseManager.isInitialized()) {
            mostrarLoading(true);
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
                    mostrarLoading(false);
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

                    carregarDados();
                } else {
                    Log.e(TAG, "Falha na inicialização do banco: " + message);

                    if (retryCount < MAX_RETRIES && isNetworkRelatedError(message)) {
                        // Tenta novamente automaticamente para erros de rede
                        retryCount++;
                        Log.w(TAG, "Tentando novamente (" + retryCount + "/" + MAX_RETRIES + ")");

                        // Pequeno delay antes de tentar novamente
                        new Handler().postDelayed(() -> {
                            DatabaseManager.initializeAsync(Home.this, this::handleInitializationCallback);
                        }, 1000);
                    } else {
                        mostrarLoading(false);
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

            carregarDados();
        }
    }

    private void handleInitializationCallback(boolean success, String message) {
        // Método para ser usado como referência de método
        if (success) {
            Log.i(TAG, "Banco de dados inicializado com sucesso");

            // Registra o listener após inicialização bem-sucedida
            DatabaseManager.addDataChangeListener(this);

            carregarDados();
        } else {
            Log.e(TAG, "Falha na inicialização do banco: " + message);
            mostrarLoading(false);
            mostrarMensagemErro("Não foi possível inicializar o banco de dados. Por favor, tente novamente.");
        }
    }

    private boolean isNetworkRelatedError(String errorMessage) {
        // Verifica se o erro está relacionado com problemas de rede
        String errorLowerCase = errorMessage.toLowerCase();
        return errorLowerCase.contains("network") ||
                errorLowerCase.contains("connection") ||
                errorLowerCase.contains("timeout") ||
                errorLowerCase.contains("unreachable") ||
                errorLowerCase.contains("internet");
    }

    private boolean isCriticalError(String errorMessage) {
        // Verifica se é um erro crítico que não pode ser resolvido sem reiniciar o app
        String errorLowerCase = errorMessage.toLowerCase();
        return errorLowerCase.contains("critical") ||
                errorLowerCase.contains("corrupted") ||
                errorLowerCase.contains("permission denied") ||
                errorLowerCase.contains("security");
    }

    private void exibirDialogoErroFatal(String mensagem) {
        new AlertDialog.Builder(this)
                .setTitle("Erro Fatal")
                .setMessage(mensagem + "\n\nO aplicativo precisará ser fechado.")
                .setPositiveButton("Fechar App", (dialog, which) -> {
                    finishAffinity(); // Fecha todas as activities
                })
                .setCancelable(false)
                .show();
    }

    private void mostrarLoading(boolean mostrar) {
        if (loadingView != null) {
            loadingView.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        }
        textoResultado.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        if (mostrar) {
            textoResultado.setText("Sincronizando dados...");
        }
    }

    private void mostrarMensagemErro(String mensagem) {
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
        textoResultado.setText(mensagem);
        textoResultado.setVisibility(View.VISIBLE);
    }

    // Implementação do método da interface DataChangeListener
    @Override
    public void onDataChanged() {
        Log.d(TAG, "Notificação de alteração de dados recebida, recarregando dados da Home");
        carregarDados();
    }

    private void carregarDados() {
        textoResultado.setText("Sincronizando dados...");
        textoResultado.setVisibility(View.VISIBLE);

        // 1. Carregar resumo financeiro
        HomeDAO.getResumoFinanceiro(new HomeDAO.HomeDataCallback<HomeDAO.ResumoFinanceiro>() {
            @Override
            public void onResult(HomeDAO.ResumoFinanceiro data, boolean success, String message) {
                if (success) {
                    // Atualizar UI com os dados do resumo financeiro
                    atualizarResumoFinanceiro(data);
                } else {
                    // Mostrar erro apenas se for relevante para o usuário
                    textoResultado.setText("Erro ao carregar resumo financeiro");
                }

                // Continuar carregando outros dados
                carregarItensEmAlta();
            }
        });
    }

    private void carregarItensEmAlta() {
        // 2. Carregar itens em alta
        HomeDAO.getItensEmAlta(new HomeDAO.HomeDataCallback<List<HomeDAO.ItemEmAlta>>() {
            @Override
            public void onResult(List<HomeDAO.ItemEmAlta> data, boolean success, String message) {
                if (success) {
                    // Atualizar UI com os itens em alta
                    atualizarItensEmAlta(data);
                } else {
                    // Log do erro, mas não mostrar ao usuário
                    txtSemItensAlta.setVisibility(View.VISIBLE);
                    recyclerItensAlta.setVisibility(View.GONE);
                }

                // Continuar carregando outros dados
                carregarProdutosEstoqueBaixo();
            }
        });
    }

    private void carregarProdutosEstoqueBaixo() {
        // 3. Carregar produtos com estoque baixo
        HomeDAO.getProdutosEstoqueBaixo(new HomeDAO.HomeDataCallback<List<HomeDAO.EstoqueBaixo>>() {
            @Override
            public void onResult(List<HomeDAO.EstoqueBaixo> data, boolean success, String message) {
                if (success) {
                    // Atualizar UI com os produtos de estoque baixo
                    atualizarProdutosEstoqueBaixo(data);
                } else {
                    // Log do erro, mas não mostrar ao usuário
                    txtSemEstoqueBaixo.setVisibility(View.VISIBLE);
                    recyclerEstoqueBaixo.setVisibility(View.GONE);
                }

                // Concluir carregamento
                textoResultado.setVisibility(View.GONE);
                mostrarLoading(false);
            }
        });
    }

    private void atualizarResumoFinanceiro(HomeDAO.ResumoFinanceiro resumo) {
        txtVendasHoje.setText("Hoje: " + (int)resumo.vendasHoje);
        txtVendasMes.setText("Mês: " + (int)resumo.vendasMes);
        txtReceitaHoje.setText("Hoje: " + currencyFormat.format(resumo.receitaHoje));
        txtReceitaMes.setText("Mês: " + currencyFormat.format(resumo.receitaMes));
    }

    private void atualizarItensEmAlta(List<HomeDAO.ItemEmAlta> itens) {
        if (itens.isEmpty()) {
            txtSemItensAlta.setVisibility(View.VISIBLE);
            recyclerItensAlta.setVisibility(View.GONE);
        } else {
            txtSemItensAlta.setVisibility(View.GONE);
            recyclerItensAlta.setVisibility(View.VISIBLE);
            itensAltaAdapter.atualizarDados(itens);
        }
    }

    private void atualizarProdutosEstoqueBaixo(List<HomeDAO.EstoqueBaixo> produtos) {
        if (produtos.isEmpty()) {
            txtSemEstoqueBaixo.setVisibility(View.VISIBLE);
            recyclerEstoqueBaixo.setVisibility(View.GONE);
        } else {
            txtSemEstoqueBaixo.setVisibility(View.GONE);
            recyclerEstoqueBaixo.setVisibility(View.VISIBLE);
            estoqueBaixoAdapter.atualizarDados(produtos);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Registra o listener ao retornar à tela
        if (DatabaseManager.isInitialized()) {
            DatabaseManager.addDataChangeListener(this);
            carregarDados();
        } else {
            verificarAutenticacaoEInicializarBanco();
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
        // Remove o listener para evitar vazamento de memória
        DatabaseManager.removeDataChangeListener(this);

        // Cancela qualquer operação pendente
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        // Cancela verificação de autenticação pendente
        if (authCheckRunnable != null) {
            authCheckHandler.removeCallbacks(authCheckRunnable);
        }

        super.onDestroy();
    }
}
