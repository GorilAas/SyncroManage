package tech.turso.SyncroManage;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseApp;
import tech.turso.SyncroManage.databinding.ActivitySplashSyncroDbBinding;

public class SplashSyncroDB extends BaseActivity {
    private ActivitySplashSyncroDbBinding binding;
    private static final String TAG = "SplashSyncroDB";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashSyncroDbBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            binding.textoVersao.setText("v" + pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            binding.textoVersao.setText("v1.0.0");
        }

        animarElementos();

        new Handler(Looper.getMainLooper()).postDelayed(this::verificarConexaoInternet, 1500);
    }

    private void animarElementos() {
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        binding.logoImage.startAnimation(fadeIn);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            binding.logoImage.setVisibility(View.VISIBLE);
        }, 300);

        AlphaAnimation fadeInCard = new AlphaAnimation(0.0f, 1.0f);
        fadeInCard.setDuration(800);
        fadeInCard.setStartOffset(800);
        binding.cardView.startAnimation(fadeInCard);
        binding.cardView.setVisibility(View.VISIBLE);
    }

    private void verificarConexaoInternet() {
        binding.textoStatus.setText("Verificando conexão com a internet...");
        atualizarProgresso(10);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (temConexaoInternet()) {
                Log.i(TAG, "Conexão com a internet OK.");
                atualizarProgresso(33);
                verificarServicosNaNuvem();
            } else {
                Log.w(TAG, "Sem conexão com a internet.");
                mostrarErro("Não foi possível conectar à internet. Verifique sua conexão e tente novamente.");
            }
        }, 1000);
    }

    private void verificarServicosNaNuvem() {
        binding.textoStatus.setText("Conectando aos serviços na nuvem...");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                FirebaseApp defaultApp = FirebaseApp.getInstance();
                Log.i(TAG, "FirebaseApp inicializado com sucesso: " + defaultApp.getName());
                atualizarProgresso(66);
                verificarBancoDeDados();

            } catch (IllegalStateException e) {
                Log.e(TAG, "Falha ao obter instância FirebaseApp. VERIFIQUE A CONFIGURAÇÃO (google-services.json, SHA keys, package name)!", e);
                mostrarErro("Falha ao inicializar os serviços na nuvem. Verifique a configuração do Firebase no seu projeto (google-services.json, chaves SHA, nome do pacote).");

            } catch (Exception e) {
                Log.e(TAG, "Erro inesperado durante a verificação de serviços na nuvem.", e);
                mostrarErro("Ocorreu um erro inesperado durante a inicialização.");
            }
        }, 1000);
    }

    private void verificarBancoDeDados() {
        binding.textoStatus.setText("Verificando disponibilidade do banco de dados...");

        // Executar a verificação em uma thread separada
        new Thread(() -> {
            final boolean isAvailable = DatabaseManager.verifyServiceAvailability();

            runOnUiThread(() -> {
                if (isAvailable) {
                    Log.i(TAG, "Serviço de banco de dados (Turso) está acessível.");
                    atualizarProgresso(100);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        startActivity(new Intent(SplashSyncroDB.this, Login.class));
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        finish();
                    }, 500);
                } else {
                    Log.e(TAG, "Falha ao acessar o serviço de banco de dados (Turso).");
                    mostrarErro("Não foi possível conectar ao banco de dados. Verifique sua conexão ou tente novamente mais tarde.");
                }
            });
        }).start();
    }

    private void atualizarProgresso(int progresso) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            binding.progressBar.setProgress(progresso, true);
        } else {
            binding.progressBar.setProgress(progresso);
        }
    }

    private boolean temConexaoInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.w(TAG, "ConnectivityManager não encontrado.");
            return false;
        }
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        Log.d(TAG, "Verificação de internet: " + (isConnected ? "Conectado" : "Desconectado"));
        return isConnected;
    }

    private void mostrarErro(String mensagem) {
        binding.progressBar.setVisibility(View.INVISIBLE);
        binding.textoStatus.setVisibility(View.GONE);
        binding.textoErro.setText(mensagem);
        binding.textoErro.setVisibility(View.VISIBLE);
        binding.botaoTentarNovamente.setVisibility(View.VISIBLE);
        binding.botaoSair.setVisibility(View.VISIBLE);

        binding.botaoTentarNovamente.setOnClickListener(v -> {
            Log.d(TAG, "Botão Tentar Novamente clicado.");
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.textoStatus.setVisibility(View.VISIBLE);
            binding.textoErro.setVisibility(View.GONE);
            binding.botaoTentarNovamente.setVisibility(View.GONE);
            binding.botaoSair.setVisibility(View.GONE);
            binding.progressBar.setProgress(0);
            verificarConexaoInternet();
        });

        binding.botaoSair.setOnClickListener(v -> {
            Log.d(TAG, "Botão Sair clicado.");
            finishAffinity();
        });
    }
}