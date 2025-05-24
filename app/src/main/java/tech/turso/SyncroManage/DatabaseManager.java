package tech.turso.SyncroManage;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Imports para iText 7

import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import tech.turso.libsql.Connection;
import tech.turso.libsql.Database;
import tech.turso.libsql.Libsql;
import tech.turso.libsql.Rows;
import tech.turso.libsql.EmbeddedReplicaDatabase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gerenciador de banco de dados para a aplicação SyncroManage.
 * Esta classe lida com inicialização, conexão e operações do banco de dados Turso,
 * incluindo suporte a réplicas locais para funcionamento offline.
 */
public class DatabaseManager {
    private static final String TAG = "DatabaseManager";

    // Constantes para URL e endpoints
    private static final String DB_URL = "link do seu turso db";
    private static final String CLOUD_FUNCTION_URL = "link aqui do cloud function";

    // Nome do arquivo para a réplica local
    private static final String LOCAL_DB_FILENAME = "syncromanage_local.db";

    // Constantes para controle de timeout e tentativas
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private static final int SYNC_INTERVAL_MS = 300000; // 5 minutos

    // Estado da inicialização e do token
    private static boolean isInitialized = false;
    private static int currentRetryAttempt = 0;
    private static final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private static String tursoToken = null;
    private static long tokenExpiryTime = 0; // Timestamp em milissegundos quando o token expira
    private static boolean useLocalReplication = false;

    // Contexto da aplicação
    private static Context appContext;

    // Lock para sincronização
    private static final ReentrantLock dbLock = new ReentrantLock();

    // Instâncias do banco de dados
    private static Database db;
    private static EmbeddedReplicaDatabase embeddedDb;

    // Handler para sincronização periódica
    private static Handler syncHandler;
    private static Runnable syncRunnable;
    private static boolean autoSyncEnabled = false;

    // Executor para operações assíncronas
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Interface para listener de estado da conexão
    public interface ConnectionStateListener {
        void onConnectionStateChanged(boolean isConnected, String message);
    }

    // Interface para listener de alterações nos dados
    public interface DataChangeListener {
        void onDataChanged();
    }

    // Interface para callback de inicialização
    public interface InitCallback {
        void onComplete(boolean success, String message);
    }

    // Interface para callback de token
    public interface TokenCallback {
        void onTokenResult(String token, boolean success, String errorMessage);
    }

    // Interface simplificada para compatibilidade
    public interface SimpleTokenCallback {
        void onTokenResult(String token, boolean success);
    }

    // Interface para callback de sincronização
    public interface SyncCallback {
        void onSyncComplete(boolean success, String message);
    }

    // Lista de listeners registrados
    private static final List<ConnectionStateListener> stateListeners = new ArrayList<>();

    // Lista de listeners de alteração de dados
    private static final List<DataChangeListener> dataListeners = new ArrayList<>();

    /**
     * Adiciona um listener para alterações nos dados
     * @param listener Listener a ser adicionado
     */
    public static void addDataChangeListener(DataChangeListener listener) {
        if (listener != null && !dataListeners.contains(listener)) {
            Log.d(TAG, "Adicionando DataChangeListener: " + listener.getClass().getSimpleName());
            dataListeners.add(listener);
        }
    }

    /**
     * Remove um listener de alterações nos dados
     * @param listener Listener a ser removido
     */
    public static void removeDataChangeListener(DataChangeListener listener) {
        if (listener != null && dataListeners.contains(listener)) {
            Log.d(TAG, "Removendo DataChangeListener: " + listener.getClass().getSimpleName());
            dataListeners.remove(listener);
        }
    }

    /**
     * Notifica todos os listeners sobre alterações nos dados
     */
    public static void notifyDataChanged() {
        Log.d(TAG, "Notificando alterações de dados para " + dataListeners.size() + " listeners");

        // Garantir que a notificação ocorra na thread principal
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Já estamos na thread principal
            for (DataChangeListener listener : new ArrayList<>(dataListeners)) {
                try {
                    listener.onDataChanged();
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao notificar listener: " + e.getMessage(), e);
                }
            }
        } else {
            // Precisamos mudar para a thread principal
            mainHandler.post(() -> {
                for (DataChangeListener listener : new ArrayList<>(dataListeners)) {
                    try {
                        listener.onDataChanged();
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao notificar listener: " + e.getMessage(), e);
                    }
                }
            });
        }
    }

    /**
     * Verifica se o DatabaseManager está inicializado
     * @return true se já inicializado, false caso contrário
     */
    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Verifica se o DatabaseManager está em processo de inicialização
     * @return true se está inicializando, false caso contrário
     */
    public static boolean isInitializing() {
        return isInitializing.get();
    }

    /**
     * Verifica se está usando réplica local
     * @return true se está usando réplica local
     */
    public static boolean isUsingLocalReplication() {
        return useLocalReplication && embeddedDb != null;
    }

    /**
     * Define se deve usar replicação local
     * @param useLocal true para usar replicação local
     */
    public static void setUseLocalReplication(boolean useLocal) {
        useLocalReplication = useLocal;
    }

    /**
     * Habilita ou desabilita sincronização automática
     * @param enabled true para habilitar
     */
    public static void setAutoSync(boolean enabled) {
        autoSyncEnabled = enabled;
        if (enabled && syncHandler != null && syncRunnable != null) {
            syncHandler.post(syncRunnable);
        } else if (!enabled && syncHandler != null && syncRunnable != null) {
            syncHandler.removeCallbacks(syncRunnable);
        }
    }

    /**
     * Verifica apenas a disponibilidade do serviço
     * @return true se o serviço está disponível
     */
    public static boolean verifyServiceAvailability() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(CLOUD_FUNCTION_URL)
                .method("OPTIONS", null)
                .build();

        try {
            Response response = client.newCall(request).execute();
            boolean isAvailable = response.isSuccessful() ||
                    response.code() == 204 ||
                    response.code() == 401 ||
                    response.code() == 403;

            Log.i(TAG, "Verificação de disponibilidade do serviço: " +
                    (isAvailable ? "Disponível" : "Indisponível") + " (código: " + response.code() + ")");
            return isAvailable;
        } catch (IOException e) {
            Log.w(TAG, "Erro ao verificar disponibilidade do serviço: " + e.getMessage());
            return false;
        }
    }

    /**
     * Inicializa o banco de dados de forma assíncrona
     * @param context Contexto da aplicação
     * @param callback Callback para notificar sobre sucesso/falha
     */
    public static void initializeAsync(Context context, InitCallback callback) {
        // Se já estiver inicializado, apenas notifica sucesso
        if (isInitialized) {
            Log.d(TAG, "Banco de dados já inicializado.");
            mainHandler.post(() -> callback.onComplete(true, "Banco de dados já inicializado"));
            return;
        }

        // Se já estiver em processo de inicialização, evita iniciar novamente
        if (!isInitializing.compareAndSet(false, true)) {
            Log.d(TAG, "Inicialização já em andamento");
            mainHandler.post(() -> callback.onComplete(false, "Inicialização já em andamento"));
            return;
        }

        // Salva o contexto da aplicação
        if (context != null) {
            appContext = context.getApplicationContext();
        }

        // Notifica mudança de estado para "conectando"
        notifyStateChange(false, "Conectando ao banco de dados...");

        // Executa a inicialização em uma thread separada
        executor.execute(() -> {
            try {
                // Obter token Turso
                refreshTursoToken((token, success, errorMessage) -> {
                    if (!success || token == null) {
                        // Verifica se pode inicializar em modo offline usando a réplica local
                        if (useLocalReplication && appContext != null) {
                            initializeLocalDatabase(callback);
                        } else {
                            handleFailure("Falha ao obter token Turso: " + errorMessage, callback);
                        }
                        return;
                    }

                    // Tentar conectar ao banco de dados com o token
                    connectToDatabase(callback);
                });
            } catch (Exception e) {
                handleFailure("Erro na inicialização: " + e.getMessage(), callback);
            }
        });
    }

    /**
     * Inicializa o banco de dados local
     */
    private static void initializeLocalDatabase(InitCallback callback) {
        executor.execute(() -> {
            try {
                Log.i(TAG, "Tentando inicializar banco de dados local");

                // Verifica se o arquivo do banco de dados existe
                File dbFile = appContext.getDatabasePath(LOCAL_DB_FILENAME);
                boolean dbExists = dbFile.exists();

                if (!dbExists) {
                    // Se o banco não existe, cria o diretório pai e notifica o usuário
                    if (!dbFile.getParentFile().exists()) {
                        dbFile.getParentFile().mkdirs();
                    }
                    Log.i(TAG, "Criando novo banco de dados local em: " + dbFile.getAbsolutePath());
                    mainHandler.post(() -> Toast.makeText(appContext,
                            "Criando banco de dados local para uso offline",
                            Toast.LENGTH_LONG).show());
                }

                // Somente prossegue se houver um token válido para a réplica local
                if (tursoToken == null) {
                    // Modo completamente offline (sem réplica)
                    Log.i(TAG, "Inicializando em modo completamente offline");
                    embeddedDb = null;
                    // Usa o método openLocal para abrir um banco de dados local
                    db = Libsql.openLocal(dbFile.getAbsolutePath());
                } else {
                    // Modo réplica local
                    Log.i(TAG, "Inicializando como réplica local com sincronização");
                    // Usa o método openEmbeddedReplica para abrir uma réplica local
                    embeddedDb = (EmbeddedReplicaDatabase) Libsql.openEmbeddedReplica(
                            dbFile.getAbsolutePath(),
                            DB_URL,
                            tursoToken);
                    db = embeddedDb;
                }

                // Teste de conexão local
                try (Connection conn = db.connect()) {
                    conn.execute("CREATE TABLE IF NOT EXISTS test (a INTEGER)");
                    conn.execute("INSERT INTO test VALUES (1)");
                    try (Rows rows = conn.query("SELECT COUNT(*) FROM test")) {
                        Object[] row = rows.nextRow();
                        if (row != null) {
                            Log.i(TAG, "Tabela 'test' OK, count: " + row[0]);

                            // Sucesso na inicialização
                            isInitialized = true;
                            isInitializing.set(false);
                            currentRetryAttempt = 0;

                            // Configura sincronização periódica se for réplica local
                            if (embeddedDb != null) {
                                setupPeriodicSync();
                            }

                            notifyStateChange(true, "Conexão local estabelecida com sucesso");
                            mainHandler.post(() -> callback.onComplete(true, "Banco de dados local inicializado com sucesso"));
                        } else {
                            handleFailure("Nenhum dado retornado na tabela 'test' do banco local", callback);
                        }
                    }
                }
            } catch (Exception e) {
                handleFailure("Erro ao conectar ao banco local: " + e.getMessage(), callback);
            }
        });
    }

    /**
     * Configura sincronização periódica para o banco de dados local
     */
    private static void setupPeriodicSync() {
        if (syncHandler == null) {
            syncHandler = new Handler(Looper.getMainLooper());
        }

        if (syncRunnable == null) {
            syncRunnable = new Runnable() {
                @Override
                public void run() {
                    // Executa sincronização se estiver habilitada
                    if (autoSyncEnabled && embeddedDb != null) {
                        syncDatabase(null);
                    }

                    // Agenda próxima execução
                    if (autoSyncEnabled) {
                        syncHandler.postDelayed(this, SYNC_INTERVAL_MS);
                    }
                }
            };

            // Inicia ciclo de sincronização se auto-sincronização estiver habilitada
            if (autoSyncEnabled) {
                syncHandler.post(syncRunnable);
            }
        }
    }

    /**
     * Cancela a inicialização atual
     */
    public static void cancelInitialization() {
        if (isInitializing.get()) {
            Log.d(TAG, "Cancelando inicialização do banco de dados em andamento");

            // Interrompe o processo de inicialização
            isInitializing.set(false);
            currentRetryAttempt = 0;

            // Notifica os listeners sobre o cancelamento
            notifyStateChange(false, "Inicialização cancelada pelo sistema");
        } else {
            Log.d(TAG, "Nenhuma inicialização em andamento para cancelar");
        }
    }

    /**
     * Sincroniza o banco de dados local com o remoto
     * @param callback Callback opcional para notificar sobre sucesso/falha
     */
    public static void syncDatabase(@Nullable SyncCallback callback) {
        // Verifica se está usando réplica local e se está inicializado
        if (embeddedDb == null || !isInitialized) {
            String errorMsg = "Sincronização não disponível: réplica local não inicializada";
            Log.w(TAG, errorMsg);
            if (callback != null) {
                mainHandler.post(() -> callback.onSyncComplete(false, errorMsg));
            }
            return;
        }

        // Verifica se o token é válido para sincronização
        long currentTime = System.currentTimeMillis();
        if (tursoToken == null || tokenExpiryTime <= currentTime) {
            // Token inválido ou expirado, tentar atualizar
            refreshTursoToken((token, success, errorMessage) -> {
                if (success && token != null) {
                    // Com token atualizado, tenta sincronizar novamente
                    performSync(callback);
                } else {
                    String errorMsg = "Não foi possível sincronizar: falha ao obter token";
                    Log.e(TAG, errorMsg + ": " + errorMessage);
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSyncComplete(false, errorMsg));
                    }
                }
            });
        } else {
            // Token válido, prossegue com a sincronização
            performSync(callback);
        }
    }

    /**
     * Executa a sincronização do banco de dados local com o remoto
     */
    private static void performSync(@Nullable SyncCallback callback) {
        executor.execute(() -> {
            // Adquire lock para evitar operações concorrentes durante a sincronização
            dbLock.lock();
            try {
                Log.i(TAG, "Iniciando sincronização do banco de dados local");

                // Executa a sincronização nativa
                embeddedDb.sync();

                Log.i(TAG, "Sincronização concluída com sucesso");

                // Notifica mudanças nos dados após sincronização
                notifyDataChanged();

                if (callback != null) {
                    mainHandler.post(() -> callback.onSyncComplete(true, "Sincronização concluída com sucesso"));
                }
            } catch (Exception e) {
                String errorMsg = "Erro durante sincronização: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSyncComplete(false, errorMsg));
                }
            } finally {
                dbLock.unlock();
            }
        });
    }

    /**
     * Conecta ao banco de dados com o token atual
     */
    private static void connectToDatabase(InitCallback callback) {
        executor.execute(() -> {
            try {
                // Verifica se deve usar réplica local
                if (useLocalReplication && appContext != null) {
                    // Inicializa banco de dados com réplica local
                    File dbFile = appContext.getDatabasePath(LOCAL_DB_FILENAME);
                    if (!dbFile.getParentFile().exists()) {
                        dbFile.getParentFile().mkdirs();
                    }

                    Log.i(TAG, "Inicializando banco de dados com réplica local em: " + dbFile.getAbsolutePath());

                    // Usa o método openEmbeddedReplica para abrir uma réplica local
                    embeddedDb = (EmbeddedReplicaDatabase) Libsql.openEmbeddedReplica(
                            dbFile.getAbsolutePath(),
                            DB_URL,
                            tursoToken);
                    db = embeddedDb;

                    // Configura sincronização periódica
                    setupPeriodicSync();
                } else {
                    // Inicializa banco de dados remoto padrão
                    Log.i(TAG, "Inicializando banco remoto padrão");
                    db = Libsql.openRemote(DB_URL, tursoToken);
                    embeddedDb = null;
                }

                // Teste de conexão
                try (Connection conn = db.connect()) {
                    conn.execute("CREATE TABLE IF NOT EXISTS test (a INTEGER)");
                    conn.execute("INSERT INTO test VALUES (1)");
                    try (Rows rows = conn.query("SELECT COUNT(*) FROM test")) {
                        Object[] row = rows.nextRow();
                        if (row != null) {
                            Log.i(TAG, "Tabela 'test' OK, count: " + row[0]);

                            // Sucesso na inicialização
                            isInitialized = true;
                            isInitializing.set(false);
                            currentRetryAttempt = 0;
                            notifyStateChange(true, "Conexão estabelecida com sucesso");
                            mainHandler.post(() -> callback.onComplete(true, "Banco de dados inicializado com sucesso"));
                        } else {
                            handleFailure("Nenhum dado retornado na tabela 'test'", callback);
                        }
                    }
                }
            } catch (Exception e) {
                // Se falhar e tiver replicação local habilitada, tenta inicializar localmente
                if (useLocalReplication && appContext != null) {
                    Log.w(TAG, "Erro ao conectar ao banco remoto: " + e.getMessage() + ". Tentando inicializar localmente.");
                    initializeLocalDatabase(callback);
                } else {
                    handleFailure("Erro ao conectar ao banco: " + e.getMessage(), callback);
                }
            }
        });
    }

    /**
     * Atualiza o token Turso quando necessário
     */
    private static void refreshTursoToken(TokenCallback callback) {
        // Verificar se temos um token atual e se ainda é válido
        long currentTime = System.currentTimeMillis();
        if (tursoToken != null && tokenExpiryTime > currentTime + 60000) { // 1 minuto de margem
            // Token ainda é válido
            mainHandler.post(() -> callback.onTokenResult(tursoToken, true, ""));
            return;
        }

        // Precisamos de um novo token
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            String errorMsg = "Nenhum usuário Firebase logado";
            Log.e(TAG, errorMsg);
            mainHandler.post(() -> callback.onTokenResult(null, false, errorMsg));
            return;
        }

        currentUser.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String idToken = task.getResult().getToken();
                fetchTursoToken(idToken, callback);
            } else {
                String errorMsg = "Falha ao obter token Firebase: " +
                        (task.getException() != null ? task.getException().getMessage() : "erro desconhecido");
                Log.e(TAG, errorMsg, task.getException());
                mainHandler.post(() -> callback.onTokenResult(null, false, errorMsg));
            }
        });
    }

    /**
     * Obtém um token Turso usando o token Firebase
     */
    private static void fetchTursoToken(String firebaseIdToken, TokenCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("idToken", firebaseIdToken);
        } catch (JSONException e) {
            String errorMsg = "Erro ao criar JSON para requisição";
            Log.e(TAG, errorMsg, e);
            mainHandler.post(() -> callback.onTokenResult(null, false, errorMsg));
            return;
        }

        Request request = new Request.Builder()
                .url(CLOUD_FUNCTION_URL)
                .post(RequestBody.create(JSON, requestBody.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMsg = "Falha na requisição HTTP: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onTokenResult(null, false, errorMsg));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String token = jsonResponse.getString("tursoToken");

                        // Armazenar o token obtido
                        tursoToken = token;
                        // Token Turso expira em 2h (7200000 ms), definimos expiração para 1h45min para margem de segurança
                        tokenExpiryTime = System.currentTimeMillis() + 6300000;

                        Log.i(TAG, "Token Turso obtido com sucesso");
                        mainHandler.post(() -> callback.onTokenResult(token, true, ""));
                    } catch (JSONException e) {
                        String errorMsg = "Erro ao processar resposta JSON: " + e.getMessage();
                        Log.e(TAG, errorMsg, e);
                        mainHandler.post(() -> callback.onTokenResult(null, false, errorMsg));
                    }
                } else {
                    String errorMsg = "Erro na resposta HTTP: " + response.code();
                    try {
                        String responseBody = response.body().string();
                        errorMsg += " - " + responseBody;
                    } catch (Exception e) {
                        // Ignorar erro ao ler o corpo da resposta
                    }
                    Log.e(TAG, errorMsg);
                    String finalErrorMsg = errorMsg;
                    mainHandler.post(() -> callback.onTokenResult(null, false, finalErrorMsg));
                }
            }
        });
    }

    /**
     * Método para retrocompatibilidade
     */
    private static void refreshTursoToken(SimpleTokenCallback callback) {
        refreshTursoToken((token, success, errorMessage) -> {
            mainHandler.post(() -> callback.onTokenResult(token, success));
        });
    }

    /**
     * Trata falhas na inicialização
     */
    private static void handleFailure(String errorMessage, InitCallback callback) {
        Log.e(TAG, "Falha: " + errorMessage);

        if (currentRetryAttempt < MAX_RETRY_ATTEMPTS) {
            currentRetryAttempt++;
            Log.d(TAG, "Tentando novamente (" + currentRetryAttempt + "/" + MAX_RETRY_ATTEMPTS + ")");

            // Espera um pouco antes de tentar novamente
            mainHandler.postDelayed(() -> {
                // Limpa sinalizador de inicialização para permitir nova tentativa
                isInitializing.set(false);

                // Tentar inicializar novamente
                initializeAsync(appContext, callback);
            }, RETRY_DELAY_MS * currentRetryAttempt);
        } else {
            // Esgotou as tentativas
            isInitializing.set(false);
            notifyStateChange(false, errorMessage);
            mainHandler.post(() -> callback.onComplete(false, errorMessage));
        }
    }

    /**
     * Método para forçar obtenção de novo token
     */
    public static void forceTokenRefresh(SimpleTokenCallback callback) {
        // Invalida o token atual
        tursoToken = null;
        tokenExpiryTime = 0;
        currentRetryAttempt = 0;

        // Cria um novo token
        refreshTursoToken(callback);
    }

    /**
     * Cancela operações em andamento e reseta estados
     */
    public static void cancelOperations() {
        // Cancela sincronização periódica
        if (syncHandler != null && syncRunnable != null) {
            syncHandler.removeCallbacks(syncRunnable);
        }

        // Cancela inicialização em andamento
        cancelInitialization();
    }

    /**
     * Adiciona um listener para mudanças de estado de conexão
     */
    public static void addConnectionStateListener(ConnectionStateListener listener) {
        if (listener != null && !stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
    }

    /**
     * Remove um listener de mudanças de estado de conexão
     */
    public static void removeConnectionStateListener(ConnectionStateListener listener) {
        stateListeners.remove(listener);
    }

    /**
     * Notifica todos os listeners sobre mudança de estado
     */
    private static void notifyStateChange(boolean isConnected, String message) {
        Log.d(TAG, "Estado de conexão: " + (isConnected ? "Conectado" : "Desconectado") + " - " + message);
        mainHandler.post(() -> {
            for (ConnectionStateListener listener : new ArrayList<>(stateListeners)) {
                try {
                    listener.onConnectionStateChanged(isConnected, message);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao notificar listener de estado: " + e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Obtém a instância do banco de dados
     * @return Instância do banco de dados
     */
    public static Database getDatabase() {
        return db;
    }

    /**
     * Obtém a instância do banco de dados de réplica local, se disponível
     * @return Instância do banco de dados de réplica local ou null se não disponível
     */
    public static EmbeddedReplicaDatabase getEmbeddedDatabase() {
        return embeddedDb;
    }

    /**
     * Verifica se o banco de dados está em modo offline
     * @return true se está em modo offline
     */
    public static boolean isOfflineMode() {
        return isInitialized && useLocalReplication && embeddedDb != null && tursoToken == null;
    }

    /**
     * Força uma sincronização manual do banco de dados
     * @param context Contexto da aplicação
     * @param callback Callback para notificar sobre sucesso/falha
     */
    public static void forceSyncDatabase(Context context, SyncCallback callback) {
        // Verifica se está usando réplica local
        if (!isUsingLocalReplication()) {
            String errorMsg = "Sincronização não disponível: réplica local não está em uso";
            Log.w(TAG, errorMsg);
            if (callback != null) {
                mainHandler.post(() -> callback.onSyncComplete(false, errorMsg));
            }
            return;
        }

        // Verifica se está inicializado
        if (!isInitialized) {
            String errorMsg = "Sincronização não disponível: banco de dados não inicializado";
            Log.w(TAG, errorMsg);
            if (callback != null) {
                mainHandler.post(() -> callback.onSyncComplete(false, errorMsg));
            }
            return;
        }

        // Executa sincronização
        syncDatabase(callback);
    }

    /**
     * Alterna entre modo online e offline
     * @param context Contexto da aplicação
     * @param useOffline true para usar modo offline
     * @param callback Callback para notificar sobre sucesso/falha
     */
    public static void toggleOfflineMode(Context context, boolean useOffline, InitCallback callback) {
        // Se já estiver no modo desejado, apenas notifica sucesso
        if (useOffline == useLocalReplication) {
            String msg = "Já está no modo " + (useOffline ? "offline" : "online");
            Log.d(TAG, msg);
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(true, msg));
            }
            return;
        }

        // Atualiza configuração
        useLocalReplication = useOffline;

        // Se estiver inicializado, precisa reinicializar com nova configuração
        if (isInitialized) {
            // Cancela operações em andamento
            cancelOperations();

            // Reseta estado
            isInitialized = false;

            // Reinicializa com nova configuração
            initializeAsync(context, callback);
        } else if (callback != null) {
            // Se não estiver inicializado, apenas notifica sucesso da mudança de configuração
            mainHandler.post(() -> callback.onComplete(true, "Modo " + (useOffline ? "offline" : "online") + " configurado"));
        }
    }

    /**
     * Limpa recursos e finaliza o gerenciador de banco de dados
     */
    public static void shutdown() {
        // Cancela sincronização periódica
        if (syncHandler != null && syncRunnable != null) {
            syncHandler.removeCallbacks(syncRunnable);
        }

        // Cancela inicialização em andamento
        cancelInitialization();

        // Limpa listeners
        stateListeners.clear();
        dataListeners.clear();

        // Reseta estados
        isInitialized = false;
        isInitializing.set(false);
        currentRetryAttempt = 0;

        // Fecha conexões de banco de dados
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao fechar banco de dados: " + e.getMessage(), e);
            }
            db = null;
        }

        embeddedDb = null;

        Log.i(TAG, "DatabaseManager finalizado");
    }
}
