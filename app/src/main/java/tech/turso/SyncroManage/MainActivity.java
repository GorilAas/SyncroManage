package tech.turso.SyncroManage;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import tech.turso.SyncroManage.databinding.ActivityMainBinding;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    private FirebaseAuth autenticacao;
    private ActivityMainBinding vinculacao;
    private SharedPreferences sharedPreferences;
    private View rootView;
    private AlertDialog progressDialog;
    private boolean isProcessingRegistration = false;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            this::onSignInResult
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vinculacao = ActivityMainBinding.inflate(getLayoutInflater());
        rootView = vinculacao.getRoot();
        setContentView(rootView);

        autenticacao = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("SyncroManagePrefs", MODE_PRIVATE);

        configurarOuvintes();
    }

    private void configurarOuvintes() {
        vinculacao.textoSenha.addTextChangedListener(new ValidacaoSenhaCompleta());

        vinculacao.textRegistro.setOnClickListener(v -> {
            if (!isProcessingRegistration) {
                registrarUsuario();
            }
        });

        vinculacao.botaoRegistrarGoogle.setOnClickListener(v -> iniciarLoginGoogle());

        vinculacao.textoIrParaLogin.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, Login.class))
        );
    }

    // Classe para validação de senha em tempo real
    private class ValidacaoSenhaCompleta implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Não é necessário implementar
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Não é necessário implementar
        }

        @Override
        public void afterTextChanged(Editable s) {
            String senha = s.toString();
            boolean senhaValida = validarSenha(senha);

            // Você pode adicionar lógica para feedback visual ao usuário aqui
            // Por exemplo, atualizar um indicador de força de senha
        }
    }

    private void registrarUsuario() {
        String email = vinculacao.textoEmail.getText().toString().trim();
        String senha = vinculacao.textoSenha.getText().toString();
        String confirmarSenha = vinculacao.textoConfirmarSenha.getText().toString().trim();

        if (email.isEmpty() || senha.isEmpty() || confirmarSenha.isEmpty()) {
            exibirMensagem("Preencha todos os campos!");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            exibirMensagem("O email informado não é válido!");
            return;
        }

        if (!senha.equals(confirmarSenha)) {
            exibirMensagem("As senhas não coincidem!");
            return;
        }

        if (!validarSenha(senha)) {
            exibirMensagem("A senha não atende aos requisitos!");
            return;
        }

        iniciarRegistro(email, senha);
    }

    private void iniciarRegistro(String email, String senha) {
        if (isProcessingRegistration) return;

        isProcessingRegistration = true;
        desabilitarControles(true);
        mostrarProgressDialog("Criando conta...");

        autenticacao.createUserWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this, task -> {
                    isProcessingRegistration = false;
                    esconderProgressDialog();
                    desabilitarControles(false);

                    if (task.isSuccessful() && autenticacao.getCurrentUser() != null) {
                        FirebaseUser user = autenticacao.getCurrentUser();
                        salvarPreferenciasUsuario(user.getEmail());
                        processarLoginSucesso(user);
                    } else {
                        String mensagemErro = task.getException() != null ?
                                task.getException().getMessage() : "Erro desconhecido ao criar conta";
                        exibirMensagem("Erro ao criar usuário: " + mensagemErro);
                    }
                });
    }

    private void iniciarLoginGoogle() {
        if (isProcessingRegistration) return;

        isProcessingRegistration = true;
        desabilitarControles(true);

        // Configurar as opções de login com Google
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Desconectar qualquer conta Google anterior antes de iniciar o login
        // Isso vai forçar a exibição do seletor de contas
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
        isProcessingRegistration = false;
        desabilitarControles(false);

        if (result.getResultCode() == RESULT_OK) {
            FirebaseUser user = autenticacao.getCurrentUser();
            processarLoginSucesso(user);
        } else {
            exibirMensagem("Falha no login com Google.");
        }
    }

    private void processarLoginSucesso(FirebaseUser user) {
        if (user == null) {
            exibirMensagem("Erro ao autenticar usuário.");
            return;
        }

        salvarPreferenciasUsuario(user.getEmail());
        startActivity(new Intent(this, Home.class));
        finish();
    }

    private void salvarPreferenciasUsuario(String email) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("email_usuario", email);
        editor.putLong("data_registro", System.currentTimeMillis());
        editor.apply();
    }

    private boolean validarSenha(String senha) {
        Pattern padrao = Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!.-_]).{8,}$");
        return padrao.matcher(senha).matches();
    }

    private void mostrarProgressDialog(String mensagem) {
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

    private void exibirMensagem(String mensagem) {
        Snackbar.make(rootView, mensagem, Snackbar.LENGTH_LONG).show();
    }

    private void desabilitarControles(boolean desabilitar) {
        vinculacao.botaoRegistrar.setEnabled(!desabilitar);
        vinculacao.botaoRegistrarGoogle.setEnabled(!desabilitar);
        vinculacao.textoEmail.setEnabled(!desabilitar);
        vinculacao.textoSenha.setEnabled(!desabilitar);
        vinculacao.textoConfirmarSenha.setEnabled(!desabilitar);
        vinculacao.textoIrParaLogin.setEnabled(!desabilitar);
    }
}