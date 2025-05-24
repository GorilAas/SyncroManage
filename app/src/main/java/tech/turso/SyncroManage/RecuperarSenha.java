package tech.turso.SyncroManage;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import tech.turso.SyncroManage.databinding.RecuperarSenhaBinding;

public class RecuperarSenha extends BaseActivity {

    private RecuperarSenhaBinding vinculacao;
    private FirebaseAuth autenticacao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vinculacao = RecuperarSenhaBinding.inflate(getLayoutInflater());
        setContentView(vinculacao.getRoot());

        Toolbar toolbar = vinculacao.toolbar;
        toolbar.setTitle("Recuperar Senha");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        autenticacao = FirebaseAuth.getInstance();

        vinculacao.botaoEnviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recuperarSenha();
            }
        });

        vinculacao.botaoVoltar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void recuperarSenha() {
        String email = vinculacao.textoEmail.getText().toString().trim();

        if (email.isEmpty()) {
            vinculacao.campoEmail.setError("Por favor, digite seu e-mail");
            return;
        }

        vinculacao.botaoEnviar.setEnabled(false);
        vinculacao.progressBar.setVisibility(View.VISIBLE);

        autenticacao.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    vinculacao.progressBar.setVisibility(View.GONE);
                    vinculacao.botaoEnviar.setEnabled(true);
                    vinculacao.layoutCampos.setVisibility(View.GONE);
                    vinculacao.scrollViewSucesso.setVisibility(View.VISIBLE);
                    vinculacao.textoEmailEnviado.setText("Enviamos um e-mail para:\n" + email);
                    vinculacao.textoInstrucoes.setText(
                            "Se este e-mail estiver cadastrado em nosso sistema, você receberá " +
                                    "instruções para redefinir sua senha. Caso não encontre o e-mail, " +
                                    "verifique sua caixa de spam ou tente novamente com outro e-mail."
                    );

                    if (!task.isSuccessful()) {
                        System.out.println("Erro no envio: " + task.getException().getMessage());
                    }
                });
    }

    private void exibirMensagem(String mensagem) {
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
    }
}