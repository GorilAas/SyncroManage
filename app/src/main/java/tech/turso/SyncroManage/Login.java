package tech.turso.SyncroManage;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;

import tech.turso.SyncroManage.databinding.LoginBinding;

public class Login extends BaseActivity {
    private static final String TAG = "Login";
    private FirebaseAuth autenticacao;
    private LoginBinding vinculacao;
    private SharedPreferences sharedPreferences;
    private View rootView;
    private AlertDialog progressDialog;
    private boolean isProcessingLogin = false;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            this::onSignInResult
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vinculacao = LoginBinding.inflate(getLayoutInflater());
        rootView = vinculacao.getRoot();
        setContentView(rootView);

        autenticacao = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("SyncroManagePrefs", MODE_PRIVATE);

        if (autenticacao.getCurrentUser() != null) {
            // Usuário já está logado, mas agora precisamos garantir que temos o token
            verificarOuObterToken(autenticacao.getCurrentUser());
            return;
        }

        vinculacao.botaoEntrar.setOnClickListener(v -> {
            String email = vinculacao.textoEmail.getText().toString().trim();
            String senha = vinculacao.textoSenha.getText().toString().trim();
            entrarUsuario(email, senha);
        });

        vinculacao.botaoEntrarGoogle.setOnClickListener(v -> iniciarLoginGoogle());

        vinculacao.textoIrParaRegistro.setOnClickListener(v ->
                startActivity(new Intent(Login.this, MainActivity.class))
        );

        if (vinculacao.textoEsqueceuSenha != null) {
            vinculacao.textoEsqueceuSenha.setOnClickListener(v ->
                    startActivity(new Intent(Login.this, RecuperarSenha.class))
            );
        }
    }

    private void entrarUsuario(String email, String senha) {
        if (email.isEmpty() || senha.isEmpty()) {
            exibirMensagem("Preencha todos os campos corretamente!");
            return;
        }

        desabilitarControles(true);
        mostrarProgressDialog("Autenticando...");

        autenticacao.signInWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Autenticação bem-sucedida, mas agora precisamos verificar o token
                        verificarOuObterToken(autenticacao.getCurrentUser());
                    } else {
                        desabilitarControles(false);
                        esconderProgressDialog();
                        exibirMensagem("Erro ao autenticar: " + (task.getException() != null ? task.getException().getMessage() : "Erro desconhecido"));
                    }
                });
    }

    private void iniciarLoginGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            List<AuthUI.IdpConfig> provedores = Arrays.asList(
                    new AuthUI.IdpConfig.GoogleBuilder().build()
            );
            Intent signInIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(provedores)
                    .setLogo(R.drawable.logo_notext_background)
                    .setTheme(R.style.Theme_SyncroManage)
                    .build();
            signInLauncher.launch(signInIntent);
        });
    }

    private void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        if (result.getResultCode() == RESULT_OK) {
            // Login com Google bem-sucedido, mas agora precisamos verificar o token
            verificarOuObterToken(autenticacao.getCurrentUser());
        } else {
            esconderProgressDialog();
            IdpResponse response = result.getIdpResponse();
            if (response != null && response.getError() != null) {
                Log.e(TAG, "Erro no login Google: " + response.getError().getMessage());
                exibirMensagem("Erro ao autenticar: " + response.getError().getMessage());
            } else {
                exibirMensagem("Login cancelado.");
            }
        }
    }

    /**
     * Verifica se temos um token válido ou tenta obter um novo
     */
    private void verificarOuObterToken(FirebaseUser user) {
        if (user == null) {
            esconderProgressDialog();
            desabilitarControles(false);
            exibirMensagem("Erro ao autenticar usuário.");
            return;
        }

        isProcessingLogin = true;
        mostrarProgressDialog("Obtendo acesso ao banco de dados...");

        // Tentar obter o token Turso através do DatabaseManager
        DatabaseManager.forceTokenRefresh((token, success) -> {
            if (success && token != null) {
                // Token obtido com sucesso
                esconderProgressDialog();
                desabilitarControles(false);
                isProcessingLogin = false;

                // Agora podemos prosseguir para a Home
                startActivity(new Intent(Login.this, Home.class));
                finish();
            } else {
                // Falha ao obter token
                esconderProgressDialog();
                mostrarDialogoTentarNovamente("Não foi possível obter acesso ao banco de dados.");
            }
        });
    }

    private void mostrarDialogoTentarNovamente(String mensagemErro) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Erro no login")
                .setMessage(mensagemErro + "\nDeseja tentar novamente?")
                .setPositiveButton("Tentar novamente", (d, which) -> verificarOuObterToken(autenticacao.getCurrentUser()))
                .setNegativeButton("Cancelar", (d, which) -> {
                    isProcessingLogin = false;
                    esconderProgressDialog();
                    desabilitarControles(false);
                    fecharTeclado();

                    // Fazer logout e reiniciar o processo de login
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(Login.this, Login.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .create();

        dialog.setOnDismissListener(d -> {
            isProcessingLogin = false;
            desabilitarControles(false);
            fecharTeclado();
        });

        dialog.show();
    }

    private void fecharTeclado() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void exibirMensagem(String mensagem) {
        Snackbar.make(rootView, mensagem, Snackbar.LENGTH_LONG)
                .setAction("OK", v -> {}).show();
    }

    private void desabilitarControles(boolean desativar) {
        vinculacao.botaoEntrar.setEnabled(!desativar);
        vinculacao.botaoEntrarGoogle.setEnabled(!desativar);
    }

    private void mostrarProgressDialog(String mensagem) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        progressDialog = new AlertDialog.Builder(this)
                .setView(getLayoutInflater().inflate(R.layout.dialog_carregamento, null))
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void esconderProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}