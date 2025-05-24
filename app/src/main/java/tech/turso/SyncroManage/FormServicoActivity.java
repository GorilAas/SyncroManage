package tech.turso.SyncroManage;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FormServicoActivity extends BaseActivity {

    private TextInputLayout tilNomeServico, tilCustoServico, tilValorServico;
    private TextInputEditText editNomeServico, editCustoServico, editValorServico;
    private MaterialButton btnSalvarServico;
    private ProgressBar progressBar;

    private Servico servicoAtual;
    private boolean modoEdicao = false;
    private Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form_servico);

        // Inicializar Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Inicializar Views
        tilNomeServico = findViewById(R.id.til_nome_servico);
        tilCustoServico = findViewById(R.id.til_custo_servico);
        tilValorServico = findViewById(R.id.til_valor_servico);
        editNomeServico = findViewById(R.id.edit_nome_servico);
        editCustoServico = findViewById(R.id.edit_custo_servico);
        editValorServico = findViewById(R.id.edit_valor_servico);
        btnSalvarServico = findViewById(R.id.btn_salvar_servico);
        progressBar = findViewById(R.id.progress_bar);

        // Verificar se é modo de edição
        if (getIntent().hasExtra("servico")) {
            modoEdicao = true;
            servicoAtual = (Servico) getIntent().getSerializableExtra("servico");
            preencherFormulario(servicoAtual);
            getSupportActionBar().setTitle("Editar Serviço");
        } else {
            getSupportActionBar().setTitle("Novo Serviço");
        }

        // Configurar listener do botão
        btnSalvarServico.setOnClickListener(v -> {
            if (validarFormulario()) {
                salvarServico();
            }
        });
    }

    private void preencherFormulario(Servico servico) {
        editNomeServico.setText(servico.getNome());
        editCustoServico.setText(String.format("%.2f", servico.getCusto_unitario()));
        editValorServico.setText(String.format("%.2f", servico.getValor_unitario()));
    }

    private boolean validarFormulario() {
        boolean valido = true;

        // Validar nome
        String nome = editNomeServico.getText().toString().trim();
        if (TextUtils.isEmpty(nome)) {
            tilNomeServico.setError("Nome do serviço é obrigatório");
            valido = false;
        } else {
            tilNomeServico.setError(null);
        }

        // Validar custo
        String custo = editCustoServico.getText().toString().trim();
        if (TextUtils.isEmpty(custo)) {
            tilCustoServico.setError("Custo do serviço é obrigatório");
            valido = false;
        } else {
            try {
                double custoValue = Double.parseDouble(custo);
                if (custoValue < 0) {
                    tilCustoServico.setError("Custo não pode ser negativo");
                    valido = false;
                } else {
                    tilCustoServico.setError(null);
                }
            } catch (NumberFormatException e) {
                tilCustoServico.setError("Valor inválido");
                valido = false;
            }
        }

        // Validar valor
        String valor = editValorServico.getText().toString().trim();
        if (TextUtils.isEmpty(valor)) {
            tilValorServico.setError("Valor do serviço é obrigatório");
            valido = false;
        } else {
            try {
                double valorValue = Double.parseDouble(valor);
                if (valorValue < 0) {
                    tilValorServico.setError("Valor não pode ser negativo");
                    valido = false;
                } else {
                    tilValorServico.setError(null);
                }
            } catch (NumberFormatException e) {
                tilValorServico.setError("Valor inválido");
                valido = false;
            }
        }

        return valido;
    }

    private void salvarServico() {
        showLoading(true);

        String nome = editNomeServico.getText().toString().trim();
        double custo = Double.parseDouble(editCustoServico.getText().toString().trim());
        double valor = Double.parseDouble(editValorServico.getText().toString().trim());

        if (modoEdicao) {
            // Modo edição
            servicoAtual.setNome(nome);
            servicoAtual.setCusto_unitario(custo);
            servicoAtual.setValor_unitario(valor);

            // Usando o método assíncrono com o novo padrão de callback
            ServicoDAO.atualizarServicoAsync(servicoAtual, new ServicoDAO.ServicoCallback<Boolean>() {
                @Override
                public void onResult(Boolean sucesso, boolean success, String message) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        if (success && sucesso != null && sucesso) {
                            Toast.makeText(FormServicoActivity.this,
                                    "Serviço atualizado com sucesso",
                                    Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(FormServicoActivity.this,
                                    "Erro ao atualizar serviço: " + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } else {
            // Modo inserção
            Servico novoServico = new Servico();
            novoServico.setNome(nome);
            novoServico.setCusto_unitario(custo);
            novoServico.setValor_unitario(valor);

            // Usando o método assíncrono com o novo padrão de callback
            ServicoDAO.inserirServicoAsync(novoServico, new ServicoDAO.ServicoCallback<Boolean>() {
                @Override
                public void onResult(Boolean sucesso, boolean success, String message) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        if (success && sucesso != null && sucesso) {
                            Toast.makeText(FormServicoActivity.this,
                                    "Serviço criado com sucesso",
                                    Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(FormServicoActivity.this,
                                    "Erro ao criar serviço: " + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSalvarServico.setEnabled(!isLoading);
        tilNomeServico.setEnabled(!isLoading);
        tilCustoServico.setEnabled(!isLoading);
        tilValorServico.setEnabled(!isLoading);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
