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

public class EstoqueDAO {
    private static final String TAG = "EstoqueDAO";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Interface para callbacks de operações de estoque.
     */
    public interface EstoqueCallback<T> {
        void onResult(T data, boolean success, String message);
    }

    /**
     * Lista todos os itens de estoque de forma assíncrona.
     * Utiliza formatação de string para montar a query.
     *
     * @param callback Callback para retornar os resultados.
     */
    public static void listarEstoqueAsync(EstoqueCallback<List<Estoque>> callback) {
        executor.execute(() -> {
            List<Estoque> listaEstoque = new ArrayList<>();
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                String errorMsg = "Usuário não autenticado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(listaEstoque, false, errorMsg));
                return;
            }

            if (!DatabaseManager.isInitialized()) {
                String errorMsg = "DatabaseManager não inicializado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(listaEstoque, false, errorMsg));
                return;
            }

            String userId = user.getUid();
            Database db = DatabaseManager.getDatabase();

            try (Connection conn = db.connect()) {
                // Monta a query com formatação de string
                String query = String.format(
                        "SELECT id_estoque, id_usuario, nome_produto, custo_unitario, valor_unitario, quantidade " +
                                "FROM estoque WHERE id_usuario = '%s' ORDER BY nome_produto",
                        userId);
                try (Rows rows = conn.query(query)) {
                    Object[] row;
                    while ((row = rows.nextRow()) != null) {
                        int idEstoque = ((Number) row[0]).intValue();
                        String idUsuario = (String) row[1];
                        String nomeProduto = (String) row[2];
                        double custoUnitario = ((Number) row[3]).doubleValue();
                        double valorUnitario = ((Number) row[4]).doubleValue();
                        int quantidade = ((Number) row[5]).intValue();

                        Estoque item = new Estoque(idEstoque, idUsuario, nomeProduto, custoUnitario, valorUnitario, quantidade);
                        listaEstoque.add(item);
                    }
                }
                mainHandler.post(() -> callback.onResult(listaEstoque, true, "Lista de estoque obtida com sucesso"));
            } catch (Exception e) {
                String errorMsg = "Erro ao listar estoque: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(listaEstoque, false, errorMsg));
            }
        });
    }

    /**
     * Lista todos os itens de estoque de forma síncrona.
     *
     * @return Lista de itens de estoque.
     */
    public static List<Estoque> listarEstoque() {
        List<Estoque> listaEstoque = new ArrayList<>();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return listaEstoque;
        }

        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "DatabaseManager não inicializado");
            return listaEstoque;
        }

        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String query = String.format(
                    "SELECT id_estoque, id_usuario, nome_produto, custo_unitario, valor_unitario, quantidade " +
                            "FROM estoque WHERE id_usuario = '%s' ORDER BY nome_produto",
                    userId);
            try (Rows rows = conn.query(query)) {
                Object[] row;
                while ((row = rows.nextRow()) != null) {
                    int idEstoque = ((Number) row[0]).intValue();
                    String idUsuario = (String) row[1];
                    String nomeProduto = (String) row[2];
                    double custoUnitario = ((Number) row[3]).doubleValue();
                    double valorUnitario = ((Number) row[4]).doubleValue();
                    int quantidade = ((Number) row[5]).intValue();

                    Estoque item = new Estoque(idEstoque, idUsuario, nomeProduto, custoUnitario, valorUnitario, quantidade);
                    listaEstoque.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar estoque", e);
        }

        return listaEstoque;
    }

    /**
     * Insere um item no estoque de forma assíncrona.
     * As querys são montadas via String.format.
     *
     * @param item     Item a ser inserido.
     * @param callback Callback para retornar o resultado.
     */
    public static void inserirItemEstoqueAsync(Estoque item, EstoqueCallback<Estoque> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                String errorMsg = "Usuário não autenticado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(item, false, errorMsg));
                return;
            }

            if (!DatabaseManager.isInitialized()) {
                String errorMsg = "DatabaseManager não inicializado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(item, false, errorMsg));
                return;
            }

            String userId = user.getUid();
            item.setId_usuario(userId);
            Database db = DatabaseManager.getDatabase();

            try (Connection conn = db.connect()) {
                String insertQuery = String.format(
                        "INSERT INTO estoque (id_usuario, nome_produto, custo_unitario, valor_unitario, quantidade) " +
                                "VALUES ('%s', '%s', %f, %f, %d)",
                        userId,
                        item.getNome_produto().replace("'", "''"),
                        item.getCusto_unitario(),
                        item.getValor_unitario(),
                        item.getQuantidade());
                conn.execute(insertQuery);

                // Obtém o id gerado após a inserção
                String lastIdQuery = "SELECT last_insert_rowid()";
                try (Rows rows = conn.query(lastIdQuery)) {
                    Object[] row = rows.nextRow();
                    if (row != null) {
                        item.setId_estoque(((Number) row[0]).intValue());

                        // Notifica alteração nos dados
                        DatabaseManager.notifyDataChanged();

                        mainHandler.post(() -> callback.onResult(item, true, "Item inserido com sucesso"));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onResult(item, false, "Falha ao obter ID do item inserido"));
            } catch (Exception e) {
                String errorMsg = "Erro ao inserir item no estoque: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(item, false, errorMsg));
            }
        });
    }

    /**
     * Insere um item no estoque de forma síncrona.
     *
     * @param item Item a ser inserido.
     * @return true se inserido com sucesso.
     */
    public static boolean inserirItemEstoque(Estoque item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return false;
        }

        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "DatabaseManager não inicializado");
            return false;
        }

        String userId = user.getUid();
        item.setId_usuario(userId);
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String insertQuery = String.format(
                    "INSERT INTO estoque (id_usuario, nome_produto, custo_unitario, valor_unitario, quantidade) " +
                            "VALUES ('%s', '%s', %f, %f, %d)",
                    userId,
                    item.getNome_produto().replace("'", "''"),
                    item.getCusto_unitario(),
                    item.getValor_unitario(),
                    item.getQuantidade());
            conn.execute(insertQuery);

            String lastIdQuery = "SELECT last_insert_rowid()";
            try (Rows rows = conn.query(lastIdQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    item.setId_estoque(((Number) row[0]).intValue());

                    // Notifica alteração nos dados
                    DatabaseManager.notifyDataChanged();

                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inserir item no estoque", e);
            return false;
        }
    }

    /**
     * Atualiza um item no estoque de forma assíncrona.
     *
     * @param item     Item a ser atualizado.
     * @param callback Callback para retornar o resultado.
     */
    public static void atualizarItemEstoqueAsync(Estoque item, EstoqueCallback<Boolean> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                String errorMsg = "Usuário não autenticado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
                return;
            }

            if (!DatabaseManager.isInitialized()) {
                String errorMsg = "DatabaseManager não inicializado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
                return;
            }

            String userId = user.getUid();
            Database db = DatabaseManager.getDatabase();

            try (Connection conn = db.connect()) {
                String updateQuery = String.format(
                        "UPDATE estoque SET nome_produto = '%s', custo_unitario = %f, valor_unitario = %f, quantidade = %d " +
                                "WHERE id_estoque = %d AND id_usuario = '%s'",
                        item.getNome_produto().replace("'", "''"),
                        item.getCusto_unitario(),
                        item.getValor_unitario(),
                        item.getQuantidade(),
                        item.getId_estoque(),
                        userId);
                conn.execute(updateQuery);

                String checkQuery = String.format(
                        "SELECT COUNT(*) FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                        item.getId_estoque(),
                        userId);
                try (Rows rows = conn.query(checkQuery)) {
                    Object[] row = rows.nextRow();
                    if (row != null) {
                        int count = ((Number) row[0]).intValue();
                        boolean success = count > 0;

                        // Notifica alteração nos dados
                        if (success) {
                            DatabaseManager.notifyDataChanged();
                        }

                        mainHandler.post(() -> callback.onResult(success, success,
                                success ? "Item atualizado com sucesso" : "Item não encontrado após atualização"));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onResult(false, false, "Falha ao verificar atualização"));
            } catch (Exception e) {
                String errorMsg = "Erro ao atualizar item do estoque: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
            }
        });
    }

    /**
     * Atualiza um item no estoque de forma síncrona.
     *
     * @param item Item a ser atualizado.
     * @return true se atualizado com sucesso.
     */
    public static boolean atualizarItemEstoque(Estoque item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return false;
        }

        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "DatabaseManager não inicializado");
            return false;
        }

        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String updateQuery = String.format(
                    "UPDATE estoque SET nome_produto = '%s', custo_unitario = %f, valor_unitario = %f, quantidade = %d " +
                            "WHERE id_estoque = %d AND id_usuario = '%s'",
                    item.getNome_produto().replace("'", "''"),
                    item.getCusto_unitario(),
                    item.getValor_unitario(),
                    item.getQuantidade(),
                    item.getId_estoque(),
                    userId);
            conn.execute(updateQuery);

            String checkQuery = String.format(
                    "SELECT COUNT(*) FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                    item.getId_estoque(),
                    userId);
            try (Rows rows = conn.query(checkQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int count = ((Number) row[0]).intValue();
                    boolean success = count > 0;

                    // Notifica alteração nos dados
                    if (success) {
                        DatabaseManager.notifyDataChanged();
                    }

                    return count > 0;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao atualizar item do estoque", e);
            return false;
        }
    }

    /**
     * Exclui um item do estoque de forma assíncrona.
     *
     * @param idEstoque ID do item a ser excluído.
     * @param callback  Callback para retornar o resultado.
     */
    public static void excluirItemEstoqueAsync(int idEstoque, EstoqueCallback<Boolean> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                String errorMsg = "Usuário não autenticado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
                return;
            }

            if (!DatabaseManager.isInitialized()) {
                String errorMsg = "DatabaseManager não inicializado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
                return;
            }

            String userId = user.getUid();
            Database db = DatabaseManager.getDatabase();

            try (Connection conn = db.connect()) {
                // Verifica se o produto está sendo usado em alguma venda
                String checkVendasQuery = String.format(
                        "SELECT COUNT(*) FROM vendas WHERE nome_item_vendido IN " +
                                "(SELECT nome_produto FROM estoque WHERE id_estoque = %d) " +
                                "AND id_usuario = '%s' AND tipo_item = 'produto'",
                        idEstoque, userId);

                try (Rows rows = conn.query(checkVendasQuery)) {
                    Object[] row = rows.nextRow();
                    if (row != null) {
                        int count = ((Number) row[0]).intValue();
                        if (count > 0) {
                            mainHandler.post(() -> callback.onResult(false, false,
                                    "Não é possível excluir este produto pois ele está sendo usado em vendas"));
                            return;
                        }
                    }
                }

                // Exclui o item
                String deleteQuery = String.format(
                        "DELETE FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                        idEstoque, userId);
                conn.execute(deleteQuery);

                // Verifica se foi excluído
                String checkQuery = String.format(
                        "SELECT COUNT(*) FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                        idEstoque, userId);
                try (Rows rows = conn.query(checkQuery)) {
                    Object[] row = rows.nextRow();
                    if (row != null) {
                        int count = ((Number) row[0]).intValue();
                        boolean success = count == 0;

                        // Notifica alteração nos dados
                        if (success) {
                            DatabaseManager.notifyDataChanged();
                        }

                        mainHandler.post(() -> callback.onResult(success, success,
                                success ? "Item excluído com sucesso" : "Item não encontrado para exclusão"));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onResult(false, false, "Falha ao verificar exclusão"));
            } catch (Exception e) {
                String errorMsg = "Erro ao excluir item do estoque: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
            }
        });
    }

    /**
     * Exclui um item do estoque de forma síncrona.
     *
     * @param idEstoque ID do item a ser excluído.
     * @return true se excluído com sucesso.
     */
    public static boolean excluirItemEstoque(int idEstoque) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return false;
        }

        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "DatabaseManager não inicializado");
            return false;
        }

        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            // Verifica se o produto está sendo usado em alguma venda
            String checkVendasQuery = String.format(
                    "SELECT COUNT(*) FROM vendas WHERE nome_item_vendido IN " +
                            "(SELECT nome_produto FROM estoque WHERE id_estoque = %d) " +
                            "AND id_usuario = '%s' AND tipo_item = 'produto'",
                    idEstoque, userId);

            try (Rows rows = conn.query(checkVendasQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int count = ((Number) row[0]).intValue();
                    if (count > 0) {
                        Log.e(TAG, "Não é possível excluir este produto pois ele está sendo usado em vendas");
                        return false;
                    }
                }
            }

            // Exclui o item
            String deleteQuery = String.format(
                    "DELETE FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                    idEstoque, userId);
            conn.execute(deleteQuery);

            // Verifica se foi excluído
            String checkQuery = String.format(
                    "SELECT COUNT(*) FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                    idEstoque, userId);
            try (Rows rows = conn.query(checkQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int count = ((Number) row[0]).intValue();
                    boolean success = count == 0;

                    // Notifica alteração nos dados
                    if (success) {
                        DatabaseManager.notifyDataChanged();
                    }

                    return count == 0;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao excluir item do estoque", e);
            return false;
        }
    }

    /**
     * Busca um item do estoque pelo ID de forma assíncrona.
     *
     * @param idEstoque ID do item a ser buscado.
     * @param callback  Callback para retornar o resultado.
     */
    public static void buscarItemEstoqueAsync(int idEstoque, EstoqueCallback<Estoque> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                String errorMsg = "Usuário não autenticado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(null, false, errorMsg));
                return;
            }

            if (!DatabaseManager.isInitialized()) {
                String errorMsg = "DatabaseManager não inicializado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(null, false, errorMsg));
                return;
            }

            String userId = user.getUid();
            Database db = DatabaseManager.getDatabase();

            try (Connection conn = db.connect()) {
                String query = String.format(
                        "SELECT id_estoque, id_usuario, nome_produto, custo_unitario, valor_unitario, quantidade " +
                                "FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                        idEstoque, userId);
                try (Rows rows = conn.query(query)) {
                    Object[] row = rows.nextRow();
                    if (row != null) {
                        int id = ((Number) row[0]).intValue();
                        String idUsuario = (String) row[1];
                        String nomeProduto = (String) row[2];
                        double custoUnitario = ((Number) row[3]).doubleValue();
                        double valorUnitario = ((Number) row[4]).doubleValue();
                        int quantidade = ((Number) row[5]).intValue();

                        Estoque item = new Estoque(id, idUsuario, nomeProduto, custoUnitario, valorUnitario, quantidade);
                        mainHandler.post(() -> callback.onResult(item, true, "Item encontrado com sucesso"));
                        return;
                    }
                    mainHandler.post(() -> callback.onResult(null, false, "Item não encontrado"));
                }
            } catch (Exception e) {
                String errorMsg = "Erro ao buscar item do estoque: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(null, false, errorMsg));
            }
        });
    }

    /**
     * Atualiza apenas a quantidade de um item no estoque de forma síncrona.
     *
     * @param idEstoque ID do item a ser atualizado.
     * @param novaQuantidade Nova quantidade do item.
     * @return true se atualizado com sucesso.
     */
    public static boolean atualizarQuantidade(int idEstoque, int novaQuantidade) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return false;
        }

        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "DatabaseManager não inicializado");
            return false;
        }

        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String updateQuery = String.format(
                    "UPDATE estoque SET quantidade = %d WHERE id_estoque = %d AND id_usuario = '%s'",
                    novaQuantidade,
                    idEstoque,
                    userId);
            conn.execute(updateQuery);

            String checkQuery = String.format(
                    "SELECT COUNT(*) FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                    idEstoque,
                    userId);
            try (Rows rows = conn.query(checkQuery)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int count = ((Number) row[0]).intValue();
                    boolean success = count > 0;

                    // Notifica alteração nos dados
                    if (success) {
                        DatabaseManager.notifyDataChanged();
                    }

                    return count > 0;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao atualizar quantidade do item", e);
            return false;
        }
    }

    /**
     * Verifica se já existe um produto com o mesmo nome no estoque.
     *
     * @param nomeProduto Nome do produto a ser verificado.
     * @return true se já existe um produto com o mesmo nome.
     */
    public static boolean verificarProdutoExistente(String nomeProduto) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return false;
        }

        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "DatabaseManager não inicializado");
            return false;
        }

        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String query = String.format(
                    "SELECT COUNT(*) FROM estoque WHERE nome_produto = '%s' AND id_usuario = '%s'",
                    nomeProduto.replace("'", "''"),
                    userId);
            try (Rows rows = conn.query(query)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int count = ((Number) row[0]).intValue();
                    return count > 0;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar existência do produto", e);
            return false;
        }
    }

    /**
     * Verifica se já existe um produto com o mesmo nome no estoque de forma assíncrona.
     *
     * @param nomeProduto Nome do produto a ser verificado.
     * @param callback Callback para retornar o resultado.
     */
    public static void verificarProdutoExistenteAsync(String nomeProduto, EstoqueCallback<Boolean> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                String errorMsg = "Usuário não autenticado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
                return;
            }

            if (!DatabaseManager.isInitialized()) {
                String errorMsg = "DatabaseManager não inicializado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
                return;
            }

            String userId = user.getUid();
            Database db = DatabaseManager.getDatabase();

            try (Connection conn = db.connect()) {
                String query = String.format(
                        "SELECT COUNT(*) FROM estoque WHERE nome_produto = '%s' AND id_usuario = '%s'",
                        nomeProduto.replace("'", "''"),
                        userId);
                try (Rows rows = conn.query(query)) {
                    Object[] row = rows.nextRow();
                    if (row != null) {
                        int count = ((Number) row[0]).intValue();
                        boolean exists = count > 0;
                        mainHandler.post(() -> callback.onResult(exists, true,
                                exists ? "Produto já existe" : "Produto não existe"));
                        return;
                    }
                    mainHandler.post(() -> callback.onResult(false, false, "Falha ao verificar existência do produto"));
                }
            } catch (Exception e) {
                String errorMsg = "Erro ao verificar existência do produto: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
            }
        });
    }

    /**
     * Atualiza apenas a quantidade de um item no estoque de forma assíncrona.
     *
     * @param idEstoque ID do item a ser atualizado.
     * @param novaQuantidade Nova quantidade do item.
     * @param callback Callback para retornar o resultado.
     */
    public static void atualizarQuantidadeAsync(int idEstoque, int novaQuantidade, EstoqueCallback<Boolean> callback) {
        executor.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user == null) {
                String errorMsg = "Usuário não autenticado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
                return;
            }

            if (!DatabaseManager.isInitialized()) {
                String errorMsg = "DatabaseManager não inicializado";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
                return;
            }

            String userId = user.getUid();
            Database db = DatabaseManager.getDatabase();

            try (Connection conn = db.connect()) {
                String updateQuery = String.format(
                        "UPDATE estoque SET quantidade = %d WHERE id_estoque = %d AND id_usuario = '%s'",
                        novaQuantidade,
                        idEstoque,
                        userId);
                conn.execute(updateQuery);

                String checkQuery = String.format(
                        "SELECT COUNT(*) FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                        idEstoque,
                        userId);
                try (Rows rows = conn.query(checkQuery)) {
                    Object[] row = rows.nextRow();
                    if (row != null) {
                        int count = ((Number) row[0]).intValue();
                        boolean success = count > 0;

                        // Notifica alteração nos dados
                        if (success) {
                            DatabaseManager.notifyDataChanged();
                        }

                        mainHandler.post(() -> callback.onResult(success, success,
                                success ? "Quantidade atualizada com sucesso" : "Item não encontrado após atualização"));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onResult(false, false, "Falha ao verificar atualização"));
            } catch (Exception e) {
                String errorMsg = "Erro ao atualizar quantidade do item: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(false, false, errorMsg));
            }
        });
    }

    /**
     * Busca um item do estoque pelo ID de forma síncrona.
     *
     * @param idEstoque ID do item a ser buscado.
     * @return Item encontrado ou null se não encontrado.
     */
    public static Estoque buscarItemEstoque(int idEstoque) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return null;
        }

        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "DatabaseManager não inicializado");
            return null;
        }

        String userId = user.getUid();
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            String query = String.format(
                    "SELECT id_estoque, id_usuario, nome_produto, custo_unitario, valor_unitario, quantidade " +
                            "FROM estoque WHERE id_estoque = %d AND id_usuario = '%s'",
                    idEstoque, userId);
            try (Rows rows = conn.query(query)) {
                Object[] row = rows.nextRow();
                if (row != null) {
                    int id = ((Number) row[0]).intValue();
                    String idUsuario = (String) row[1];
                    String nomeProduto = (String) row[2];
                    double custoUnitario = ((Number) row[3]).doubleValue();
                    double valorUnitario = ((Number) row[4]).doubleValue();
                    int quantidade = ((Number) row[5]).intValue();

                    return new Estoque(id, idUsuario, nomeProduto, custoUnitario, valorUnitario, quantidade);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar item do estoque", e);
        }

        return null;
    }
}
