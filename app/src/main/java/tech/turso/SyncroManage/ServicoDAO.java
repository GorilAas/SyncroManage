package tech.turso.SyncroManage;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tech.turso.libsql.Connection;
import tech.turso.libsql.Database;
import tech.turso.libsql.Rows;

public class ServicoDAO {
    private static final String TAG = "ServicoDAO";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Interface para callbacks de operações de serviço.
     */
    public interface ServicoCallback<T> {
        void onResult(T data, boolean success, String message);
    }

    /**
     * Lista os serviços de forma assíncrona usando _string formatting_.
     *
     * @param callback Callback para receber o resultado da operação.
     */
    public static void listarServicosAsync(ServicoCallback<List<Servico>> callback) {
        executor.execute(() -> {
            List<Servico> servicos = new ArrayList<>();
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                Log.e(TAG, "Usuário não autenticado");
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(servicos, false, "Usuário não autenticado"));
                }
                return;
            }

            String userId = user.getUid();

            if (!DatabaseManager.isInitialized()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(servicos, false, "Banco de dados não inicializado"));
                }
                return;
            }

            try {
                Database db = DatabaseManager.getDatabase();
                try (Connection conn = db.connect()) {
                    String query = String.format(
                            "SELECT id_servico, id_usuario, nome, custo_unitario, valor_unitario " +
                                    "FROM servicos WHERE id_usuario = '%s'", userId);

                    try (Rows rows = conn.query(query)) {
                        Object[] row;
                        while ((row = rows.nextRow()) != null) {
                            int id_servico = ((Number) row[0]).intValue();
                            String id_usuario = (String) row[1];
                            String nome = (String) row[2];
                            double custo_unitario = ((Number) row[3]).doubleValue();
                            double valor_unitario = ((Number) row[4]).doubleValue();

                            Servico servico = new Servico(id_servico, id_usuario, nome, custo_unitario, valor_unitario);
                            servicos.add(servico);
                        }
                    }

                    if (callback != null) {
                        mainHandler.post(() -> callback.onResult(servicos, true, "Serviços listados com sucesso"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao listar serviços", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(servicos, false, "Erro ao listar serviços: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Lista os serviços de forma síncrona usando _string formatting_.
     *
     * @return Lista de serviços.
     */
    public static List<Servico> listarServicos() {
        List<Servico> servicos = new ArrayList<>();

        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "Banco de dados não inicializado");
            return servicos;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return servicos;
        }

        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String query = String.format(
                    "SELECT id_servico, id_usuario, nome, custo_unitario, valor_unitario " +
                            "FROM servicos WHERE id_usuario = '%s'", userId);

            try (Rows rows = conn.query(query)) {
                Object[] row;
                while ((row = rows.nextRow()) != null) {
                    int id_servico = ((Number) row[0]).intValue();
                    String id_usuario = (String) row[1];
                    String nome = (String) row[2];
                    double custo_unitario = ((Number) row[3]).doubleValue();
                    double valor_unitario = ((Number) row[4]).doubleValue();

                    Servico servico = new Servico(id_servico, id_usuario, nome, custo_unitario, valor_unitario);
                    servicos.add(servico);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar serviços", e);
        }

        return servicos;
    }

    /**
     * Insere um serviço de forma assíncrona utilizando _string formatting_ para montar a query.
     *
     * @param servico  Serviço a ser inserido.
     * @param callback Callback para receber o resultado da operação.
     */
    public static void inserirServicoAsync(Servico servico, ServicoCallback<Boolean> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                Log.e(TAG, "Usuário não autenticado");
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Usuário não autenticado"));
                }
                return;
            }

            if (!DatabaseManager.isInitialized()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Banco de dados não inicializado"));
                }
                return;
            }

            String userId = user.getUid();
            servico.setId_usuario(userId);

            try {
                Database db = DatabaseManager.getDatabase();
                try (Connection conn = db.connect()) {
                    String insertQuery = String.format(
                            "INSERT INTO servicos (id_usuario, nome, custo_unitario, valor_unitario) " +
                                    "VALUES ('%s', '%s', %f, %f)",
                            userId,
                            servico.getNome().replace("'", "''"),
                            servico.getCusto_unitario(),
                            servico.getValor_unitario());
                    conn.execute(insertQuery);

                    // Recupera o último ID inserido
                    String lastIdQuery = "SELECT last_insert_rowid()";
                    try (Rows rows = conn.query(lastIdQuery)) {
                        Object[] row = rows.nextRow();
                        if (row != null) {
                            servico.setId_servico(((Number) row[0]).intValue());
                            if (callback != null) {
                                mainHandler.post(() -> callback.onResult(true, true, "Serviço inserido com sucesso"));
                            }
                            return;
                        }
                    }

                    if (callback != null) {
                        mainHandler.post(() -> callback.onResult(false, false, "Não foi possível obter o ID gerado"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao inserir serviço", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Erro ao inserir serviço: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Insere um serviço de forma síncrona utilizando _string formatting_.
     *
     * @param servico Serviço a ser inserido.
     * @return true se a operação foi bem-sucedida.
     */
    public static boolean inserirServico(Servico servico) {
        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "Banco de dados não inicializado");
            return false;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return false;
        }

        String userId = user.getUid();
        servico.setId_usuario(userId);
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String insertQuery = String.format(
                    "INSERT INTO servicos (id_usuario, nome, custo_unitario, valor_unitario) " +
                            "VALUES ('%s', '%s', %f, %f)",
                    userId,
                    servico.getNome().replace("'", "''"),
                    servico.getCusto_unitario(),
                    servico.getValor_unitario());
            conn.execute(insertQuery);

            String lastIdQuery = "SELECT last_insert_rowid()";
            try (Rows rows = conn.query(lastIdQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    servico.setId_servico(((Number) row[0]).intValue());
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inserir serviço", e);
            return false;
        }
    }

    /**
     * Atualiza um serviço de forma assíncrona utilizando _string formatting_.
     *
     * @param servico  Serviço a ser atualizado.
     * @param callback Callback para receber o resultado da operação.
     */
    public static void atualizarServicoAsync(Servico servico, ServicoCallback<Boolean> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.e(TAG, "Usuário não autenticado");
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Usuário não autenticado"));
                }
                return;
            }
            if (!DatabaseManager.isInitialized()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Banco de dados não inicializado"));
                }
                return;
            }
            String userId = user.getUid();

            try {
                Database db = DatabaseManager.getDatabase();
                try (Connection conn = db.connect()) {
                    String updateQuery = String.format(
                            "UPDATE servicos SET nome = '%s', custo_unitario = %f, valor_unitario = %f " +
                                    "WHERE id_servico = %d AND id_usuario = '%s'",
                            servico.getNome().replace("'", "''"),
                            servico.getCusto_unitario(),
                            servico.getValor_unitario(),
                            servico.getId_servico(),
                            userId);
                    conn.execute(updateQuery);

                    String checkQuery = String.format(
                            "SELECT COUNT(*) FROM servicos WHERE id_servico = %d AND id_usuario = '%s'",
                            servico.getId_servico(),
                            userId);
                    try (Rows rows = conn.query(checkQuery)) {
                        Object[] row = rows.nextRow();
                        if (row != null) {
                            int count = ((Number) row[0]).intValue();
                            boolean success = count > 0;
                            if (callback != null) {
                                mainHandler.post(() -> callback.onResult(success, true,
                                        success ? "Serviço atualizado com sucesso" : "Serviço não encontrado após atualização"));
                            }
                            return;
                        }
                    }
                    if (callback != null) {
                        mainHandler.post(() -> callback.onResult(false, false, "Não foi possível verificar a atualização"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao atualizar serviço", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Erro ao atualizar serviço: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Atualiza um serviço de forma síncrona utilizando _string formatting_.
     *
     * @param servico Serviço a ser atualizado.
     * @return true se a operação foi bem-sucedida.
     */
    public static boolean atualizarServico(Servico servico) {
        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "Banco de dados não inicializado");
            return false;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return false;
        }
        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String updateQuery = String.format(
                    "UPDATE servicos SET nome = '%s', custo_unitario = %f, valor_unitario = %f " +
                            "WHERE id_servico = %d AND id_usuario = '%s'",
                    servico.getNome().replace("'", "''"),
                    servico.getCusto_unitario(),
                    servico.getValor_unitario(),
                    servico.getId_servico(),
                    userId);
            conn.execute(updateQuery);

            String checkQuery = String.format(
                    "SELECT COUNT(*) FROM servicos WHERE id_servico = %d AND id_usuario = '%s'",
                    servico.getId_servico(),
                    userId);
            try (Rows rows = conn.query(checkQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int count = ((Number) row[0]).intValue();
                    return count > 0;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao atualizar serviço", e);
            return false;
        }
    }

    /**
     * Exclui um serviço de forma assíncrona utilizando _string formatting_.
     *
     * @param idServico ID do serviço a ser excluído.
     * @param callback  Callback para receber o resultado da operação.
     */
    public static void excluirServicoAsync(int idServico, ServicoCallback<Boolean> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.e(TAG, "Usuário não autenticado");
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Usuário não autenticado"));
                }
                return;
            }
            if (!DatabaseManager.isInitialized()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Banco de dados não inicializado"));
                }
                return;
            }
            String userId = user.getUid();

            try {
                Database db = DatabaseManager.getDatabase();
                try (Connection conn = db.connect()) {
                    String checkVendasQuery = String.format(
                            "SELECT COUNT(*) FROM vendas WHERE id_servico_vendido = %d AND id_usuario = '%s'",
                            idServico, userId);
                    try (Rows rows = conn.query(checkVendasQuery)) {
                        Object[] row = rows.nextRow();
                        if (row != null) {
                            int count = ((Number) row[0]).intValue();
                            if (count > 0) {
                                Log.w(TAG, "Serviço está sendo usado em vendas e não pode ser excluído");
                                if (callback != null) {
                                    mainHandler.post(() -> callback.onResult(false, true,
                                            "Serviço está sendo usado em vendas e não pode ser excluído"));
                                }
                                return;
                            }
                        }
                    }

                    String deleteQuery = String.format(
                            "DELETE FROM servicos WHERE id_servico = %d AND id_usuario = '%s'",
                            idServico, userId);
                    conn.execute(deleteQuery);

                    String checkQuery = String.format(
                            "SELECT COUNT(*) FROM servicos WHERE id_servico = %d AND id_usuario = '%s'",
                            idServico, userId);
                    try (Rows rows = conn.query(checkQuery)) {
                        Object[] row = rows.nextRow();
                        if (row != null) {
                            int count = ((Number) row[0]).intValue();
                            boolean success = count == 0;
                            if (callback != null) {
                                mainHandler.post(() -> callback.onResult(success, true,
                                        success ? "Serviço excluído com sucesso" : "Falha ao excluir serviço"));
                            }
                            return;
                        }
                    }
                    if (callback != null) {
                        mainHandler.post(() -> callback.onResult(false, false, "Não foi possível verificar a exclusão"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao excluir serviço", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(false, false, "Erro ao excluir serviço: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Exclui um serviço de forma síncrona utilizando _string formatting_.
     *
     * @param idServico ID do serviço a ser excluído.
     * @return true se a operação foi bem-sucedida.
     */
    public static boolean excluirServico(int idServico) {
        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "Banco de dados não inicializado");
            return false;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return false;
        }
        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String checkVendasQuery = String.format(
                    "SELECT COUNT(*) FROM vendas WHERE id_servico_vendido = %d AND id_usuario = '%s'",
                    idServico, userId);
            try (Rows rows = conn.query(checkVendasQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int count = ((Number) row[0]).intValue();
                    if (count > 0) {
                        Log.w(TAG, "Serviço está sendo usado em vendas e não pode ser excluído");
                        return false;
                    }
                }
            }

            String deleteQuery = String.format(
                    "DELETE FROM servicos WHERE id_servico = %d AND id_usuario = '%s'",
                    idServico, userId);
            conn.execute(deleteQuery);

            String checkQuery = String.format(
                    "SELECT COUNT(*) FROM servicos WHERE id_servico = %d AND id_usuario = '%s'",
                    idServico, userId);
            try (Rows rows = conn.query(checkQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int count = ((Number) row[0]).intValue();
                    return count == 0;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao excluir serviço", e);
            return false;
        }
    }
}
