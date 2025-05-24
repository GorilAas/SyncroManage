package tech.turso.SyncroManage;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FormVendaActivity extends BaseActivity {

    private static final String TAG = "FormVendaActivity";
    public static final String EXTRA_VENDA_EDITAR = "extra_venda_editar";

    private Toolbar toolbar;
    private RadioGroup radioGroupTipoItem;
    private RadioButton radioProduto, radioServico;
    private MaterialCardView cardSelecaoProduto, cardSelecaoServico, cardDetalhesVenda;

    private AutoCompleteTextView actvProduto;
    private TextInputEditText etValorUnitarioProduto, etQuantidadeProduto;
    private TextView tvEstoqueDisponivel;
    private ArrayAdapter<Estoque> produtoAdapter;
    private List<Estoque> listaOriginalProdutos = new ArrayList<>();
    private Estoque produtoSelecionado;

    private AutoCompleteTextView actvServico;
    private TextInputEditText etValorUnitarioServico, etQuantidadeServico;
    private ArrayAdapter<Servico> servicoAdapter;
    private List<Servico> listaOriginalServicos = new ArrayList<>();
    private Servico servicoSelecionado;

    private TextInputEditText etDataHoraVenda;
    private AutoCompleteTextView actvMetodoPagamento;
    private TextView tvValorTotalVenda;

    private MaterialButton btnSalvarVenda, btnCancelarVenda;
    private View progressBarFormVenda;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NumberFormat formatoMoeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private final SimpleDateFormat formatoDataHoraDB = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat formatoDataHoraDisplay = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    private final String[] metodoPagamentoOpcoes = {
            "Pix",
            "Dinheiro",
            "Cartão de Débito",
            "Cartão de Crédito",
            "Vale Alimentação"
    };
    private ArrayAdapter<String> metodoPagamentoAdapter;

    private Venda vendaParaEditar;
    private boolean isEditMode = false;
    private String nomeItemOriginalEdicao = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form_venda);

        if (getIntent().hasExtra(EXTRA_VENDA_EDITAR)) {
            vendaParaEditar = (Venda) getIntent().getSerializableExtra(EXTRA_VENDA_EDITAR);
            isEditMode = true;
            if (vendaParaEditar != null) {
                nomeItemOriginalEdicao = vendaParaEditar.getNome_item_vendido();
            }
        }

        setupToolbar();
        bindViews();
        setupInitialVisibility();
        setupAdapters();
        setupListeners();

        if (isEditMode && vendaParaEditar != null) {
            setTitle("Editar Venda");
            preencherFormularioParaEdicao();
        } else {
            setTitle("Nova Venda");
            etDataHoraVenda.setText(formatoDataHoraDisplay.format(new Date()));
            radioProduto.setChecked(true);
            alternarVisibilidadeCampos("produto");
        }

        carregarProdutos();
        carregarServicos();
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar_form_venda);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void bindViews() {
        radioGroupTipoItem = findViewById(R.id.radio_group_tipo_item);
        radioProduto = findViewById(R.id.radio_produto);
        radioServico = findViewById(R.id.radio_servico);
        cardSelecaoProduto = findViewById(R.id.card_selecao_produto);
        cardSelecaoServico = findViewById(R.id.card_selecao_servico);
        cardDetalhesVenda = findViewById(R.id.card_detalhes_venda);

        actvProduto = findViewById(R.id.actv_produto);
        etValorUnitarioProduto = findViewById(R.id.et_valor_unitario_produto);
        etQuantidadeProduto = findViewById(R.id.et_quantidade_produto);
        tvEstoqueDisponivel = findViewById(R.id.tv_estoque_disponivel);

        actvServico = findViewById(R.id.actv_servico);
        etValorUnitarioServico = findViewById(R.id.et_valor_unitario_servico);
        etQuantidadeServico = findViewById(R.id.et_quantidade_servico);

        etDataHoraVenda = findViewById(R.id.et_data_hora_venda);
        actvMetodoPagamento = findViewById(R.id.actv_metodo_pagamento);
        tvValorTotalVenda = findViewById(R.id.tv_valor_total_venda);

        btnSalvarVenda = findViewById(R.id.btn_salvar_venda);
        btnCancelarVenda = findViewById(R.id.btn_cancelar_venda);
        progressBarFormVenda = findViewById(R.id.progress_bar_form_venda);
    }

    private void setupInitialVisibility() {
        if (isEditMode && vendaParaEditar != null) {
            if ("produto".equals(vendaParaEditar.getTipo_item())) {
                alternarVisibilidadeCampos("produto");
            } else {
                alternarVisibilidadeCampos("servico");
            }
        } else {
            alternarVisibilidadeCampos("produto");
        }
    }

    private void setupAdapters() {
        produtoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        actvProduto.setAdapter(produtoAdapter);

        servicoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        actvServico.setAdapter(servicoAdapter);

        metodoPagamentoAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                metodoPagamentoOpcoes
        );
        actvMetodoPagamento.setAdapter(metodoPagamentoAdapter);
    }

    private void setupListeners() {
        radioGroupTipoItem.setOnCheckedChangeListener((group, checkedId) -> {
            limparCamposItemEspecifico();
            if (checkedId == R.id.radio_produto) {
                alternarVisibilidadeCampos("produto");
            } else if (checkedId == R.id.radio_servico) {
                alternarVisibilidadeCampos("servico");
            }
            calcularValorTotal();
        });

        actvProduto.setOnItemClickListener((parent, view, position, id) -> {
            produtoSelecionado = (Estoque) parent.getItemAtPosition(position);
            if (produtoSelecionado != null) {
                etValorUnitarioProduto.setText(String.format(Locale.US, "%.2f", produtoSelecionado.getValor_unitario()));
                tvEstoqueDisponivel.setText("Estoque disponível: " + produtoSelecionado.getQuantidade());
            }
            calcularValorTotal();
        });

        actvServico.setOnItemClickListener((parent, view, position, id) -> {
            servicoSelecionado = (Servico) parent.getItemAtPosition(position);
            if (servicoSelecionado != null) {
                etValorUnitarioServico.setText(String.format(Locale.US, "%.2f", servicoSelecionado.getValor_unitario()));
            }
            calcularValorTotal();
        });

        TextWatcher textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { calcularValorTotal(); }
        };

        etQuantidadeProduto.addTextChangedListener(textWatcher);
        etQuantidadeServico.addTextChangedListener(textWatcher);

        etDataHoraVenda.setOnClickListener(v -> mostrarDateTimePicker());

        btnSalvarVenda.setOnClickListener(v -> salvarVenda());
        btnCancelarVenda.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private void alternarVisibilidadeCampos(String tipo) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) cardDetalhesVenda.getLayoutParams();
        if ("produto".equals(tipo)) {
            cardSelecaoProduto.setVisibility(View.VISIBLE);
            cardSelecaoServico.setVisibility(View.GONE);
            params.topToBottom = R.id.card_selecao_produto;
        } else {
            cardSelecaoProduto.setVisibility(View.GONE);
            cardSelecaoServico.setVisibility(View.VISIBLE);
            params.topToBottom = R.id.card_selecao_servico;
        }
        cardDetalhesVenda.setLayoutParams(params);
    }

    private void limparCamposItemEspecifico() {
        actvProduto.setText("", false);
        etValorUnitarioProduto.setText("");
        etQuantidadeProduto.setText("");
        tvEstoqueDisponivel.setText("Estoque disponível: -");
        produtoSelecionado = null;

        actvServico.setText("", false);
        etValorUnitarioServico.setText("");
        etQuantidadeServico.setText("");
        servicoSelecionado = null;
    }

    private void carregarProdutos() {
        progressBarFormVenda.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            List<Estoque> produtos = EstoqueDAO.listarEstoque();
            listaOriginalProdutos.clear();
            if (produtos != null) {
                listaOriginalProdutos.addAll(produtos);
            }

            handler.post(() -> {
                produtoAdapter.clear();
                produtoAdapter.addAll(listaOriginalProdutos);
                produtoAdapter.notifyDataSetChanged();
                progressBarFormVenda.setVisibility(View.GONE);
                if (isEditMode && "produto".equals(vendaParaEditar.getTipo_item())) {
                    selecionarItemNoAutoComplete(actvProduto, listaOriginalProdutos, vendaParaEditar.getNome_item_vendido());
                }
            });
        });
    }

    private void carregarServicos() {
        progressBarFormVenda.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            List<Servico> servicos = ServicoDAO.listarServicos();
            listaOriginalServicos.clear();
            if (servicos != null) {
                listaOriginalServicos.addAll(servicos);
            }

            handler.post(() -> {
                servicoAdapter.clear();
                servicoAdapter.addAll(listaOriginalServicos);
                servicoAdapter.notifyDataSetChanged();
                progressBarFormVenda.setVisibility(View.GONE);
                if (isEditMode && "servico".equals(vendaParaEditar.getTipo_item())) {
                    selecionarItemNoAutoComplete(actvServico, listaOriginalServicos, vendaParaEditar.getNome_item_vendido());
                }
            });
        });
    }

    private <T> void selecionarItemNoAutoComplete(AutoCompleteTextView actv, List<T> lista, String nome) {
        if (nome == null || lista == null) return;
        for (int i = 0; i < lista.size(); i++) {
            T item = lista.get(i);
            if (item.toString().equalsIgnoreCase(nome)) {
                actv.setText(item.toString(), false);
                break;
            }
        }
    }

    private void mostrarDateTimePicker() {
        final Calendar calendar = Calendar.getInstance();
        try {
            if (etDataHoraVenda.getText() != null && !etDataHoraVenda.getText().toString().isEmpty()) {
                Date data = formatoDataHoraDisplay.parse(etDataHoraVenda.getText().toString());
                if (data != null) calendar.setTime(data);
            }
        } catch (ParseException e) {
            Log.w(TAG, "Erro ao parsear data existente", e);
        }

        DatePickerDialog datePicker = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    calendar.set(year, month, day);
                    TimePickerDialog timePicker = new TimePickerDialog(this,
                            (timeView, hour, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hour);
                                calendar.set(Calendar.MINUTE, minute);
                                etDataHoraVenda.setText(formatoDataHoraDisplay.format(calendar.getTime()));
                            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
                    timePicker.show();
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePicker.show();
    }

    private void preencherFormularioParaEdicao() {
        if (vendaParaEditar == null) return;

        if ("produto".equals(vendaParaEditar.getTipo_item())) {
            radioProduto.setChecked(true);
            etQuantidadeProduto.setText(String.valueOf(vendaParaEditar.getQuantidade()));
        } else {
            radioServico.setChecked(true);
            etQuantidadeServico.setText(String.valueOf(vendaParaEditar.getQuantidade()));
        }

        alternarVisibilidadeCampos(vendaParaEditar.getTipo_item());

        try {
            Date data = formatoDataHoraDB.parse(vendaParaEditar.getData_hora_venda());
            if (data != null) {
                etDataHoraVenda.setText(formatoDataHoraDisplay.format(data));
            }
        } catch (ParseException e) {
            etDataHoraVenda.setText(vendaParaEditar.getData_hora_venda());
        }

        actvMetodoPagamento.setText(vendaParaEditar.getMetodo_pagamento(), false);
    }

    private void calcularValorTotal() {
        double valor = 0;
        int qtd = 0;
        try {
            if (radioProduto.isChecked() && produtoSelecionado != null) {
                valor = produtoSelecionado.getValor_unitario();
                if (!etQuantidadeProduto.getText().toString().isEmpty())
                    qtd = Integer.parseInt(etQuantidadeProduto.getText().toString());
            } else if (radioServico.isChecked() && servicoSelecionado != null) {
                valor = servicoSelecionado.getValor_unitario();
                if (!etQuantidadeServico.getText().toString().isEmpty())
                    qtd = Integer.parseInt(etQuantidadeServico.getText().toString());
            }
        } catch (Exception e) {
            valor = 0;
            qtd = 0;
        }
        tvValorTotalVenda.setText(formatoMoeda.format(valor * qtd));
    }

    private void salvarVenda() {
        String tipoItem;
        String nomeItemVendido;
        double valorUnitarioVendido;
        int quantidadeVendida;
        Integer idServicoVendido = null;

        if (radioProduto.isChecked()) {
            tipoItem = "produto";
            if (produtoSelecionado == null || actvProduto.getText().toString().isEmpty()) {
                Toast.makeText(this, "Selecione um produto", Toast.LENGTH_SHORT).show();
                return;
            }
            nomeItemVendido = produtoSelecionado.getNome_produto();
            valorUnitarioVendido = produtoSelecionado.getValor_unitario();
            if (etQuantidadeProduto.getText().toString().isEmpty()) {
                Toast.makeText(this, "Informe a quantidade", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                quantidadeVendida = Integer.parseInt(etQuantidadeProduto.getText().toString());
                if (quantidadeVendida <= 0) {
                    Toast.makeText(this, "A quantidade deve ser maior que zero", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (quantidadeVendida > produtoSelecionado.getQuantidade()) {
                    Toast.makeText(this, "Quantidade indisponível em estoque", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Quantidade inválida", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            tipoItem = "servico";
            if (servicoSelecionado == null || actvServico.getText().toString().isEmpty()) {
                Toast.makeText(this, "Selecione um serviço", Toast.LENGTH_SHORT).show();
                return;
            }
            nomeItemVendido = servicoSelecionado.getNome();
            idServicoVendido = servicoSelecionado.getId_servico();
            valorUnitarioVendido = servicoSelecionado.getValor_unitario();
            if (etQuantidadeServico.getText().toString().isEmpty()) {
                Toast.makeText(this, "Informe a quantidade", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                quantidadeVendida = Integer.parseInt(etQuantidadeServico.getText().toString());
                if (quantidadeVendida <= 0) {
                    Toast.makeText(this, "A quantidade deve ser maior que zero", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Quantidade inválida", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (etDataHoraVenda.getText().toString().isEmpty()) {
            Toast.makeText(this, "Informe a data e hora da venda", Toast.LENGTH_SHORT).show();
            return;
        }

        String dataHoraVenda;
        try {
            Date data = formatoDataHoraDisplay.parse(etDataHoraVenda.getText().toString());
            if (data != null) {
                dataHoraVenda = formatoDataHoraDB.format(data);
            } else {
                dataHoraVenda = formatoDataHoraDB.format(new Date());
            }
        } catch (ParseException e) {
            dataHoraVenda = formatoDataHoraDB.format(new Date());
        }

        if (actvMetodoPagamento.getText().toString().isEmpty()) {
            Toast.makeText(this, "Informe o método de pagamento", Toast.LENGTH_SHORT).show();
            return;
        }
        String metodoPagamento = actvMetodoPagamento.getText().toString();

        double valorTotalVenda = valorUnitarioVendido * quantidadeVendida;

        Venda venda;
        if (isEditMode) {
            venda = vendaParaEditar;
            venda.setTipo_item(tipoItem);
            venda.setId_servico_vendido(idServicoVendido);
            venda.setNome_item_vendido(nomeItemVendido);
            venda.setValor_unitario_vendido(valorUnitarioVendido);
            venda.setQuantidade(quantidadeVendida);
            venda.setValor_total_venda(valorTotalVenda);
            venda.setData_hora_venda(dataHoraVenda);
            venda.setMetodo_pagamento(metodoPagamento);
        } else {
            venda = new Venda(
                    0, // ID será gerado pelo banco
                    "", // ID do usuário será preenchido pelo DAO
                    tipoItem,
                    idServicoVendido,
                    nomeItemVendido,
                    valorUnitarioVendido,
                    quantidadeVendida,
                    valorTotalVenda,
                    dataHoraVenda,
                    metodoPagamento
            );
        }

        progressBarFormVenda.setVisibility(View.VISIBLE);
        btnSalvarVenda.setEnabled(false);
        btnCancelarVenda.setEnabled(false);

        // Usando os métodos assíncronos do VendaDAO com callbacks
        if (isEditMode) {
            VendaDAO.atualizarVendaAsync(venda, new VendaDAO.VendaCallback<Boolean>() {
                @Override
                public void onResult(Boolean result, boolean success, String message) {
                    handler.post(() -> {
                        progressBarFormVenda.setVisibility(View.GONE);
                        btnSalvarVenda.setEnabled(true);
                        btnCancelarVenda.setEnabled(true);

                        if (success && result != null && result) {
                            Log.d(TAG, "Venda atualizada com sucesso: " + message);
                            Toast.makeText(FormVendaActivity.this,
                                    "Venda atualizada com sucesso!",
                                    Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Log.e(TAG, "Erro ao atualizar venda: " + message);
                            Toast.makeText(FormVendaActivity.this,
                                    "Erro ao atualizar venda: " + message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        } else {
            VendaDAO.inserirVendaAsync(venda, new VendaDAO.VendaCallback<Boolean>() {
                @Override
                public void onResult(Boolean result, boolean success, String message) {
                    handler.post(() -> {
                        progressBarFormVenda.setVisibility(View.GONE);
                        btnSalvarVenda.setEnabled(true);
                        btnCancelarVenda.setEnabled(true);

                        if (success && result != null && result) {
                            Log.d(TAG, "Venda inserida com sucesso: " + message);
                            Toast.makeText(FormVendaActivity.this,
                                    "Venda salva com sucesso!",
                                    Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Log.e(TAG, "Erro ao salvar venda: " + message);
                            Toast.makeText(FormVendaActivity.this,
                                    "Erro ao salvar venda: " + message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}
