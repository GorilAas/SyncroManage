package tech.turso.SyncroManage;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Relatorios extends BaseActivity implements DatabaseManager.DataChangeListener {

    // Constantes para evitar strings literais repetidas
    private static final String TAG = "Relatorios";
    private static final String DATE_FORMAT_DISPLAY = "dd/MM/yyyy";
    private static final String DATE_FORMAT_DB = "yyyy-MM-dd";
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    // Constantes para timeout e retry
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int TIMEOUT_MS = 30000;

    // UI Components
    private EditText edtNome, edtDocumento, edtEmpresa;
    private TextView tvDataInicio, tvDataFim, tvResultadoSummary;
    private Button btnGerarRelatorio, btnExportarExcel, btnGerarPDF;
    private MaterialCardView cardPreview;
    private RecyclerView rvPreview;
    private ProgressDialog progressDialog;

    // Adapter
    private RecyclerView.Adapter<?> adapter;

    // Dados do relatório
    private List<String[]> dadosRelatorio;

    // Datas selecionadas (formato para BD: "yyyy-MM-dd")
    private String dataInicioStr, dataFimStr;

    // Para operações em background
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Controle de inicialização do banco de dados
    private boolean isInitializingDatabase = false;
    private int currentRetryCount = 0;
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_relatorios);

        setupNavigationDrawer(
                R.id.drawer_layout,
                R.id.toolbar,
                R.id.nav_view,
                R.id.nav_relatorio
        );

        // Inicializa os componentes da interface
        inicializarComponentes();

        // Configurar RecyclerView
        configurarRecyclerView();

        // Configurar datas padrão (último mês)
        configurarDatasIniciais();

        // Inicialmente, os botões de exportação, compartilhamento e card de preview ficam ocultos
        atualizarEstadoBotoes(false);

        // Configuração de ações dos componentes
        configurarListeners();

        // Inicializar banco de dados antes de qualquer operação
        initializeDatabase();
    }

    private void inicializarComponentes() {
        edtNome = findViewById(R.id.edt_nome);
        edtDocumento = findViewById(R.id.edt_documento);
        edtEmpresa = findViewById(R.id.edt_empresa);
        tvDataInicio = findViewById(R.id.tv_data_inicio);
        tvDataFim = findViewById(R.id.tv_data_fim);
        btnGerarRelatorio = findViewById(R.id.btn_gerar_relatorio);
        btnExportarExcel = findViewById(R.id.btn_gerar_xml);
        btnGerarPDF = findViewById(R.id.btn_gerar_pdf);
        cardPreview = findViewById(R.id.card_preview);
        rvPreview = findViewById(R.id.rv_preview);
        tvResultadoSummary = findViewById(R.id.tv_resultado_summary);

        // Inicializar ProgressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Processando...");
        progressDialog.setCancelable(false);
    }

    /**
     * Inicializa o banco de dados de forma assíncrona com retry e timeout
     */
    private void initializeDatabase() {
        // Evita inicializações múltiplas simultâneas
        if (isInitializingDatabase) {
            Log.d(TAG, "Inicialização do banco já em andamento. Ignorando nova solicitação.");
            return;
        }

        // Verifica se o banco já está inicializado antes de tentar inicializar
        if (DatabaseManager.isInitialized()) {
            Log.d(TAG, "Banco de dados já inicializado.");
            // Registra o listener se o banco já estiver inicializado
            DatabaseManager.addDataChangeListener(this);
            return;
        }

        // Marca que uma inicialização está em andamento
        isInitializingDatabase = true;
        currentRetryCount = 0;

        // Mostra o progresso enquanto verifica o banco
        showLoading(true);

        // Configura timeout
        timeoutRunnable = () -> {
            if (isInitializingDatabase) {
                Log.e(TAG, "Timeout na inicialização do banco de dados");
                cancelOperations();
                handleDatabaseInitError("Tempo esgotado", "O banco de dados demorou muito para responder");
            }
        };

        // Agenda o timeout
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        // Inicia a tentativa de inicialização
        performDatabaseInitialization();
    }

    /**
     * Realiza a tentativa de inicialização do banco de dados
     */
    private void performDatabaseInitialization() {
        Log.d(TAG, "Tentando inicializar o banco de dados. Tentativa " + (currentRetryCount + 1) + " de " + MAX_RETRY_ATTEMPTS);

        DatabaseManager.initializeAsync(this, (success, message) -> {
            // Cancela o timeout quando recebe uma resposta
            handler.removeCallbacks(timeoutRunnable);

            if (success) {
                Log.d(TAG, "Banco de dados inicializado com sucesso");
                isInitializingDatabase = false;
                hideLoading();

                // Registra o listener após inicialização bem-sucedida
                DatabaseManager.addDataChangeListener(this);

                // Operações após sucesso na inicialização podem ser adicionadas aqui
            } else {
                Log.e(TAG, "Falha na inicialização do banco: " + message);

                // Verifica se deve tentar novamente baseado no tipo de erro
                if (currentRetryCount < MAX_RETRY_ATTEMPTS && isNetworkError(message)) {
                    currentRetryCount++;

                    // Mostra feedback de "tentando novamente"
                    runOnUiThread(() -> {
                        progressDialog.setMessage("Tentando novamente... (" + currentRetryCount + "/" + MAX_RETRY_ATTEMPTS + ")");
                    });

                    // Agenda nova tentativa com pequeno delay
                    handler.postDelayed(this::performDatabaseInitialization, RETRY_DELAY_MS);
                } else {
                    // Esgotou as tentativas ou erro crítico
                    isInitializingDatabase = false;
                    hideLoading();
                    handleDatabaseInitError("Falha na conexão", simplifyErrorMessage(message));
                }
            }
        });
    }

    /**
     * Determina se o erro é relacionado à rede e pode ser tentado novamente
     */
    private boolean isNetworkError(String errorMessage) {
        String lowerCaseError = errorMessage.toLowerCase();
        return lowerCaseError.contains("timeout") ||
                lowerCaseError.contains("connection") ||
                lowerCaseError.contains("rede") ||
                lowerCaseError.contains("internet") ||
                lowerCaseError.contains("socket") ||
                lowerCaseError.contains("servidor");
    }

    /**
     * Determina se o erro é crítico (corrupção de banco, permissão, etc)
     */
    private boolean isCriticalError(String errorMessage) {
        String lowerCaseError = errorMessage.toLowerCase();
        return lowerCaseError.contains("corrupt") ||
                lowerCaseError.contains("permiss") ||
                lowerCaseError.contains("acesso negado") ||
                lowerCaseError.contains("schema") ||
                lowerCaseError.contains("versão") ||
                lowerCaseError.contains("incompatível");
    }

    /**
     * Simplifica as mensagens de erro para o usuário
     */
    private String simplifyErrorMessage(String technicalMessage) {
        if (isNetworkError(technicalMessage)) {
            return "Problemas de conexão. Verifique sua internet.";
        } else if (isCriticalError(technicalMessage)) {
            return "Problema crítico no banco de dados.";
        } else {
            return "Não foi possível acessar os dados. Tente novamente mais tarde.";
        }
    }

    /**
     * Trata erro de inicialização do banco de dados
     */
    private void handleDatabaseInitError(String titulo, String mensagem) {
        // Para erros críticos, apresenta diálogo com opção de sair
        if (isCriticalError(mensagem)) {
            new AlertDialog.Builder(this)
                    .setTitle("Erro Grave")
                    .setMessage("Um problema crítico foi encontrado no banco de dados. O aplicativo pode não funcionar corretamente.")
                    .setPositiveButton("Tentar novamente", (dialog, which) -> {
                        initializeDatabase();
                    })
                    .setNegativeButton("Fechar aplicativo", (dialog, which) -> {
                        finishAffinity(); // Fecha o aplicativo completamente
                    })
                    .setCancelable(false)
                    .show();
        } else {
            // Para erros menos graves, mostra Snackbar com opção de retry
            Snackbar.make(
                    findViewById(android.R.id.content),
                    titulo + ": " + mensagem,
                    Snackbar.LENGTH_INDEFINITE
            ).setAction("Tentar novamente", view -> initializeDatabase()).show();
        }
    }

    /**
     * Cancela operações em andamento e limpa recursos
     */
    private void cancelOperations() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }

        isInitializingDatabase = false;
        hideLoading();
    }

    /**
     * Mostra o indicador de carregamento
     */
    private void showLoading(boolean show) {
        if (show && !progressDialog.isShowing()) {
            progressDialog.show();
        } else if (!show && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * Esconde o indicador de carregamento
     */
    private void hideLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {
                // Ignora exceções comuns ao fechar diálogos
            }
        }
    }

    private void configurarRecyclerView() {
        rvPreview.setLayoutManager(new LinearLayoutManager(this));
        rvPreview.setHasFixedSize(true); // Otimização quando sabemos que o tamanho não vai mudar
        adapter = new RelatorioAdapter(new ArrayList<>());
        rvPreview.setAdapter(adapter);
    }

    private void configurarListeners() {
        tvDataInicio.setOnClickListener(v -> selecionarDataInicio());
        tvDataFim.setOnClickListener(v -> selecionarDataFim());
        btnGerarRelatorio.setOnClickListener(v -> gerarRelatorio());
        btnExportarExcel.setOnClickListener(v -> exportarExcel());
        btnGerarPDF.setOnClickListener(v -> exportarPDF());
    }

    private void configurarDatasIniciais() {
        // Obtém o Calendar com o fuso horário local
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        Date dataFim = cal.getTime();
        cal.add(Calendar.MONTH, -1);
        Date dataInicio = cal.getTime();

        // Formatadores de data - criados uma única vez para eficiência
        SimpleDateFormat sdfDisplay = new SimpleDateFormat(DATE_FORMAT_DISPLAY, Locale.getDefault());
        SimpleDateFormat sdfDB = new SimpleDateFormat(DATE_FORMAT_DB, Locale.getDefault());

        tvDataFim.setText(sdfDisplay.format(dataFim));
        tvDataInicio.setText(sdfDisplay.format(dataInicio));

        dataFimStr = sdfDB.format(dataFim);
        dataInicioStr = sdfDB.format(dataInicio);
    }

    private void selecionarDataInicio() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecione a data inicial")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            // Ajustar para o fuso horário local
            Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
            calendar.setTimeInMillis(selection);

            SimpleDateFormat sdfDisplay = new SimpleDateFormat(DATE_FORMAT_DISPLAY, Locale.getDefault());
            SimpleDateFormat sdfDB = new SimpleDateFormat(DATE_FORMAT_DB, Locale.getDefault());

            Date selectedDate = calendar.getTime();
            tvDataInicio.setText(sdfDisplay.format(selectedDate));
            dataInicioStr = sdfDB.format(selectedDate);
        });

        datePicker.show(getSupportFragmentManager(), "DATA_INICIO_PICKER");
    }

    private void selecionarDataFim() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecione a data final")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
            calendar.setTimeInMillis(selection);

            SimpleDateFormat sdfDisplay = new SimpleDateFormat(DATE_FORMAT_DISPLAY, Locale.getDefault());
            SimpleDateFormat sdfDB = new SimpleDateFormat(DATE_FORMAT_DB, Locale.getDefault());

            Date selectedDate = calendar.getTime();
            tvDataFim.setText(sdfDisplay.format(selectedDate));
            dataFimStr = sdfDB.format(selectedDate);
        });

        datePicker.show(getSupportFragmentManager(), "DATA_FIM_PICKER");
    }

    /**
     * Atualiza o estado (habilitado/desabilitado) dos botões que dependem do relatório.
     */
    private void atualizarEstadoBotoes(boolean haDados) {
        btnExportarExcel.setEnabled(haDados);
        btnGerarPDF.setEnabled(haDados);
        cardPreview.setVisibility(haDados ? View.VISIBLE : View.GONE);
        tvResultadoSummary.setVisibility(haDados ? View.VISIBLE : View.GONE);
    }

    /**
     * Exibe feedback visual ao usuário com um SnackBar customizado
     */
    private void mostrarFeedback(View ancoragem, String mensagem, boolean longDuration) {
        Snackbar.make(
                ancoragem,
                mensagem,
                longDuration ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT
        ).show();
    }

    /**
     * Gera o relatório de vendas agrupado para o período selecionado.
     * Usa threading para não bloquear a UI durante o processamento.
     */
    private void gerarRelatorio() {
        // Validar entrada
        String nome = edtNome.getText().toString().trim();
        String documento = edtDocumento.getText().toString().trim();
        String empresa = edtEmpresa.getText().toString().trim();

        if (nome.isEmpty() || documento.isEmpty() || empresa.isEmpty()) {
            mostrarFeedback(btnGerarRelatorio, "Preencha todos os campos obrigatórios.", true);
            return;
        }

        // Verificar inicialização do banco
        if (!DatabaseManager.isInitialized()) {
            mostrarFeedback(btnGerarRelatorio, "Banco de dados não inicializado. Aguarde...", true);
            initializeDatabase();
            return;
        }

        showLoading(true);

        // Chamar o DAO para obter os dados do relatório de forma assíncrona
        RelatorioDAO.getRelatorioVendasAgrupadoAsync(dataInicioStr, dataFimStr, new RelatorioDAO.RelatorioCallback<List<String[]>>() {
            @Override
            public void onResult(List<String[]> dados, boolean success, String message) {
                hideLoading();

                if (success && dados != null) {
                    // Salvar os dados para uso posterior (exportação)
                    dadosRelatorio = dados;

                    // Atualizar o adapter com os novos dados
                    if (adapter instanceof RelatorioAdapter) {
                        ((RelatorioAdapter) adapter).atualizarDados(dados);
                    }

                    // Mostrar o card de preview e habilitar botões
                    atualizarEstadoBotoes(true);

                    // Atualizar o resumo
                    atualizarResumo(dados);

                    // Feedback positivo
                    mostrarFeedback(btnGerarRelatorio, "Relatório gerado com sucesso!", false);
                } else {
                    // Feedback negativo
                    mostrarFeedback(btnGerarRelatorio, "Erro ao gerar relatório: " + message, true);
                    atualizarEstadoBotoes(false);
                }
            }
        });
    }

    /**
     * Atualiza o resumo do relatório
     */
    private void atualizarResumo(List<String[]> dados) {
        if (dados == null || dados.isEmpty()) {
            tvResultadoSummary.setText("Nenhum dado encontrado");
            return;
        }

        // Calcular totais
        int totalVendas = dados.size();
        double valorTotal = 0;

        // Pular a linha de cabeçalho (índice 0)
        for (int i = 1; i < dados.size(); i++) {
            String[] linha = dados.get(i);
            if (linha.length >= 4) { // Garantir que há uma coluna de valor
                try {
                    // Assumindo que o valor está na coluna 3 (índice 3)
                    String valorStr = linha[3].replace("R$", "").replace(".", "").replace(",", ".");
                    valorTotal += Double.parseDouble(valorStr);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    Log.e(TAG, "Erro ao processar valor: " + e.getMessage());
                }
            }
        }

        // Formatar e exibir o resumo
        String resumo = String.format(
                "Total de vendas: %d | Valor total: %s",
                totalVendas - 1, // Subtrair 1 para não contar o cabeçalho
                CURRENCY_FORMAT.format(valorTotal)
        );

        tvResultadoSummary.setText(resumo);
    }

    /**
     * Exporta o relatório para Excel
     */
    private void exportarExcel() {
        if (dadosRelatorio == null || dadosRelatorio.isEmpty()) {
            mostrarFeedback(btnExportarExcel, "Nenhum dado para exportar. Gere o relatório primeiro.", true);
            return;
        }

        String nome = edtNome.getText().toString().trim();
        String documento = edtDocumento.getText().toString().trim();
        String empresa = edtEmpresa.getText().toString().trim();

        showLoading(true);

        // Usar o RelatorioDAO para exportar para Excel de forma assíncrona
        RelatorioDAO.exportarExcelAsync(this, dadosRelatorio, new RelatorioDAO.RelatorioCallback<File>() {
            @Override
            public void onResult(File arquivo, boolean success, String message) {
                hideLoading();

                if (success && arquivo != null) {
                    // Compartilhar o arquivo gerado
                    RelatorioDAO.compartilharArquivo(Relatorios.this, arquivo, "application/vnd.ms-excel");
                    mostrarFeedback(btnExportarExcel, "Excel gerado com sucesso!", false);
                } else {
                    mostrarFeedback(btnExportarExcel, "Erro ao gerar Excel: " + message, true);
                }
            }
        });
    }

    /**
     * Exporta o relatório para PDF
     */
    private void exportarPDF() {
        if (dadosRelatorio == null || dadosRelatorio.isEmpty()) {
            mostrarFeedback(btnGerarPDF, "Nenhum dado para exportar. Gere o relatório primeiro.", true);
            return;
        }

        String nome = edtNome.getText().toString().trim();
        String documento = edtDocumento.getText().toString().trim();
        String empresa = edtEmpresa.getText().toString().trim();

        showLoading(true);

        // Usar o RelatorioDAO para exportar para PDF de forma assíncrona
        RelatorioDAO.exportarPDFAsync(this, dadosRelatorio, nome, documento, empresa, new RelatorioDAO.RelatorioCallback<File>() {
            @Override
            public void onResult(File arquivo, boolean success, String message) {
                hideLoading();

                if (success && arquivo != null) {
                    // Compartilhar o arquivo gerado
                    RelatorioDAO.compartilharArquivo(Relatorios.this, arquivo, "application/pdf");
                    mostrarFeedback(btnGerarPDF, "PDF gerado com sucesso!", false);
                } else {
                    mostrarFeedback(btnGerarPDF, "Erro ao gerar PDF: " + message, true);
                }
            }
        });
    }

    // Implementação do método da interface DataChangeListener
    @Override
    public void onDataChanged() {
        Log.d(TAG, "Notificação de alteração de dados recebida, verificando necessidade de atualizar relatório");

        // Se já tiver um relatório gerado, atualiza automaticamente
        if (dadosRelatorio != null && !dadosRelatorio.isEmpty()) {
            gerarRelatorio();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Registra o listener ao retornar à tela
        if (DatabaseManager.isInitialized()) {
            DatabaseManager.addDataChangeListener(this);
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

        // Limpar recursos
        cancelOperations();
        executor.shutdown();
    }
}
