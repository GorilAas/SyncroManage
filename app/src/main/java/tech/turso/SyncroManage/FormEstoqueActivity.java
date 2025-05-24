package tech.turso.SyncroManage;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FormEstoqueActivity extends BaseActivity {

    private TextInputEditText editNomeProduto, editCustoUnitario, editValorUnitario, editQuantidade;
    private Button buttonSalvar, buttonCancelar;
    private ProgressBar progressBar;
    private Estoque itemEstoque;
    private boolean modoEdicao = false;
    private Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form_estoque);

        // Inicializar toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Inicializar componentes de UI
        editNomeProduto = findViewById(R.id.editNomeProduto);
        editCustoUnitario = findViewById(R.id.editCustoUnitario);
        editValorUnitario = findViewById(R.id.editValorUnitario);
        editQuantidade = findViewById(R.id.editQuantidade);
        buttonSalvar = findViewById(R.id.buttonSalvar);
        buttonCancelar = findViewById(R.id.buttonCancelar);
        progressBar = findViewById(R.id.progressBar);

        // Verificar se é edição ou inserção
        itemEstoque = (Estoque) getIntent().getSerializableExtra("item_estoque");

        if (itemEstoque != null) {
            modoEdicao = true;
            getSupportActionBar().setTitle("Editar Produto");
            preencherCampos();
        } else {
            getSupportActionBar().setTitle("Novo Produto");
            itemEstoque = new Estoque();
            editQuantidade.setText("0"); // Valor inicial padrão para quantidade
        }

        // Configurar listeners
        buttonSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validarCampos()) {
                    prepararItemESalvar();
                }
            }
        });

        buttonCancelar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void preencherCampos() {
        editNomeProduto.setText(itemEstoque.getNome_produto());
        editCustoUnitario.setText(String.valueOf(itemEstoque.getCusto_unitario()));
        editValorUnitario.setText(String.valueOf(itemEstoque.getValor_unitario()));
        editQuantidade.setText(String.valueOf(itemEstoque.getQuantidade()));
    }

    private boolean validarCampos() {
        String nomeProduto = editNomeProduto.getText().toString().trim();
        String custoUnitarioStr = editCustoUnitario.getText().toString().trim();
        String valorUnitarioStr = editValorUnitario.getText().toString().trim();
        String quantidadeStr = editQuantidade.getText().toString().trim();

        if (nomeProduto.isEmpty()) {
            editNomeProduto.setError("Nome do produto é obrigatório");
            editNomeProduto.requestFocus();
            return false;
        }

        if (custoUnitarioStr.isEmpty()) {
            editCustoUnitario.setError("Custo unitário é obrigatório");
            editCustoUnitario.requestFocus();
            return false;
        }

        if (valorUnitarioStr.isEmpty()) {
            editValorUnitario.setError("Valor unitário é obrigatório");
            editValorUnitario.requestFocus();
            return false;
        }

        if (quantidadeStr.isEmpty()) {
            editQuantidade.setError("Quantidade é obrigatória");
            editQuantidade.requestFocus();
            return false;
        }

        try {
            double custoUnitario = Double.parseDouble(custoUnitarioStr);
            if (custoUnitario < 0) {
                editCustoUnitario.setError("Custo unitário não pode ser negativo");
                editCustoUnitario.requestFocus();
                return false;
            }
        } catch (NumberFormatException e) {
            editCustoUnitario.setError("Formato inválido");
            editCustoUnitario.requestFocus();
            return false;
        }

        try {
            double valorUnitario = Double.parseDouble(valorUnitarioStr);
            if (valorUnitario < 0) {
                editValorUnitario.setError("Valor unitário não pode ser negativo");
                editValorUnitario.requestFocus();
                return false;
            }
        } catch (NumberFormatException e) {
            editValorUnitario.setError("Formato inválido");
            editValorUnitario.requestFocus();
            return false;
        }

        try {
            int quantidade = Integer.parseInt(quantidadeStr);
            if (quantidade < 0) {
                editQuantidade.setError("Quantidade não pode ser negativa");
                editQuantidade.requestFocus();
                return false;
            }
        } catch (NumberFormatException e) {
            editQuantidade.setError("Formato inválido");
            editQuantidade.requestFocus();
            return false;
        }

        return true;
    }

    private void prepararItemESalvar() {
        String nomeProduto = editNomeProduto.getText().toString().trim();
        double custoUnitario = Double.parseDouble(editCustoUnitario.getText().toString().trim());
        double valorUnitario = Double.parseDouble(editValorUnitario.getText().toString().trim());
        int quantidade = Integer.parseInt(editQuantidade.getText().toString().trim());

        itemEstoque.setNome_produto(nomeProduto);
        itemEstoque.setCusto_unitario(custoUnitario);
        itemEstoque.setValor_unitario(valorUnitario);
        itemEstoque.setQuantidade(quantidade);

        progressBar.setVisibility(View.VISIBLE);
        buttonSalvar.setEnabled(false);
        buttonCancelar.setEnabled(false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean sucesso;

                if (modoEdicao) {
                    sucesso = EstoqueDAO.atualizarItemEstoque(itemEstoque);
                } else {
                    // Verificar se já existe um produto com este nome
                    boolean produtoExistente = EstoqueDAO.verificarProdutoExistente(itemEstoque.getNome_produto());

                    if (produtoExistente) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                buttonSalvar.setEnabled(true);
                                buttonCancelar.setEnabled(true);
                                editNomeProduto.setError("Já existe um produto com este nome");
                                editNomeProduto.requestFocus();
                            }
                        });
                        return;
                    }

                    sucesso = EstoqueDAO.inserirItemEstoque(itemEstoque);
                }

                final boolean resultadoFinal = sucesso;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        if (resultadoFinal) {
                            Toast.makeText(FormEstoqueActivity.this,
                                    modoEdicao ? "Produto atualizado com sucesso!" : "Produto adicionado com sucesso!",
                                    Toast.LENGTH_SHORT).show();

                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("item_estoque", itemEstoque);
                            setResult(RESULT_OK, resultIntent);
                            finish();
                        } else {
                            buttonSalvar.setEnabled(true);
                            buttonCancelar.setEnabled(true);
                            Toast.makeText(FormEstoqueActivity.this,
                                    "Erro ao " + (modoEdicao ? "atualizar" : "adicionar") + " produto. Tente novamente.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
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