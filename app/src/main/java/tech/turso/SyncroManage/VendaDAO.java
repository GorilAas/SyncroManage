package tech.turso.SyncroManage;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tech.turso.libsql.Connection;
import tech.turso.libsql.Database;
import tech.turso.libsql.Rows;

/**
 * DAO para operações relacionadas a vendas.
 * Adaptado para utilizar o sistema de callbacks do DatabaseManager.
 * Mantém métodos síncronos e assíncronos.
 * Nota: As queries SQL são montadas usando String.format, conforme exigido pela biblioteca Turso em seu estágio atual.
 */
public class VendaDAO {
    private static final String TAG = "VendaDAO";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Interface de callback para operações com vendas.
     */
    public interface VendaCallback<T> {
        void onResult(T result, boolean success, String message);
    }

    /**
     * Interface de callback para operações com produtos.
     */
    public interface ProdutoCallback<T> {
        void onResult(T result, boolean success, String message);
    }

    /**
     * Interface de callback para operações com serviços.
     */
    public interface ServicoCallback<T> {
        void onResult(T result, boolean success, String message);
    }

    // --- MÉTODOS PARA PRODUTOS ---

    /**
     * Lista todos os produtos do usuário autenticado de forma assíncrona.
     * Acessa diretamente a tabela de estoque.
     */
    public static void listarProdutosAsync(ProdutoCallback<List<Estoque>> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            String errorMsg = "Usuário não autenticado";
            Log.e(TAG, errorMsg);
            mainHandler.post(() -> callback.onResult(new ArrayList<>(), false, errorMsg));
            return;
        }

        if (!DatabaseManager.isInitialized()) {
            String errorMsg = "DatabaseManager não inicializado. Tente novamente mais tarde.";
            Log.w(TAG, errorMsg);
            mainHandler.post(() -> callback.onResult(new ArrayList<>(), false, errorMsg));
            return;
        }

        final String userId = user.getUid();
        Log.d(TAG, "Iniciando consulta de produtos para usuário: " + userId);

        executor.execute(() -> {
            List<Estoque> produtos = new ArrayList<>();
            Connection conn = null;
            try {
                Database db = DatabaseManager.getDatabase();
                if (db == null) {
                    throw new IllegalStateException("DatabaseManager.getDatabase() retornou null apesar de inicializado.");
                }

                Log.d(TAG, "Obteve instância do banco de dados, executando query de produtos");

                conn = db.connect();
                // Primeiro, verifica se a tabela existe e tem dados
                String checkTableQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='estoque'";
                boolean tabelaExiste = false;

                try (Rows checkRows = conn.query(checkTableQuery)) {
                    tabelaExiste = checkRows.nextRow() != null;
                }

                if (!tabelaExiste) {
                    Log.e(TAG, "Tabela 'estoque' não existe no banco de dados");
                    mainHandler.post(() -> callback.onResult(produtos, true, "Nenhum produto encontrado"));
                    return;
                }

                // Verifica se há produtos para este usuário
                String countQuery = String.format(
                        "SELECT COUNT(*) FROM estoque WHERE id_usuario = '%s'",
                        userId
                );

                int count = 0;
                try (Rows countRows = conn.query(countQuery)) {
                    Object[] countRow = countRows.nextRow();
                    if (countRow != null) {
                        count = ((Number) countRow[0]).intValue();
                    }
                }

                Log.d(TAG, "Contagem de produtos para o usuário " + userId + ": " + count);

                // Se não houver produtos, retorna lista vazia com sucesso
                if (count == 0) {
                    Log.i(TAG, "Nenhum produto encontrado para o usuário: " + userId);
                    mainHandler.post(() -> callback.onResult(produtos, true, "Nenhum produto encontrado"));
                    return;
                }

                // Se houver produtos, busca os dados
                String query = String.format(
                        "SELECT id_estoque, id_usuario, nome_produto, custo_unitario, valor_unitario, quantidade " +
                                "FROM estoque WHERE id_usuario = '%s' ORDER BY nome_produto",
                        userId
                );

                Log.d(TAG, "Executando query de produtos: " + query);

                try (Rows rows = conn.query(query)) {
                    Object[] row;
                    while ((row = rows.nextRow()) != null) {
                        try {
                            int idEstoque = ((Number) row[0]).intValue();
                            String idUsuario = (String) row[1];
                            String nomeProduto = (String) row[2];
                            double custoUnitario = ((Number) row[3]).doubleValue();
                            double valorUnitario = ((Number) row[4]).doubleValue();
                            int quantidade = ((Number) row[5]).intValue();

                            Estoque produto = new Estoque(idEstoque, idUsuario, nomeProduto, custoUnitario, valorUnitario, quantidade);
                            produtos.add(produto);
                            Log.d(TAG, "Produto adicionado: ID=" + idEstoque + ", Nome=" + nomeProduto);
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao processar linha de produto: " + e.getMessage(), e);
                            // Continua processando outras linhas
                        }
                    }
                }

                Log.i(TAG, "Produtos carregados com sucesso: " + produtos.size() + " registros");
                mainHandler.post(() -> callback.onResult(produtos, true, "Produtos carregados com sucesso"));
            } catch (Exception e) {
                String errorMsg = "Erro ao listar produtos: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(produtos, false, errorMsg));
            } finally {
                // Garantir que a conexão seja fechada
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao fechar conexão: " + e.getMessage());
                    }
                }
            }
        });
    }

    // --- MÉTODOS PARA SERVIÇOS ---

    /**
     * Lista todos os serviços do usuário autenticado de forma assíncrona.
     * Acessa diretamente a tabela de serviços.
     */
    public static void listarServicosAsync(ServicoCallback<List<Servico>> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            String errorMsg = "Usuário não autenticado";
            Log.e(TAG, errorMsg);
            mainHandler.post(() -> callback.onResult(new ArrayList<>(), false, errorMsg));
            return;
        }

        if (!DatabaseManager.isInitialized()) {
            String errorMsg = "DatabaseManager não inicializado. Tente novamente mais tarde.";
            Log.w(TAG, errorMsg);
            mainHandler.post(() -> callback.onResult(new ArrayList<>(), false, errorMsg));
            return;
        }

        final String userId = user.getUid();
        Log.d(TAG, "Iniciando consulta de serviços para usuário: " + userId);

        executor.execute(() -> {
            List<Servico> servicos = new ArrayList<>();
            Connection conn = null;
            try {
                Database db = DatabaseManager.getDatabase();
                if (db == null) {
                    throw new IllegalStateException("DatabaseManager.getDatabase() retornou null apesar de inicializado.");
                }

                Log.d(TAG, "Obteve instância do banco de dados, executando query de serviços");

                conn = db.connect();
                // Primeiro, verifica se a tabela existe e tem dados
                String checkTableQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='servicos'";
                boolean tabelaExiste = false;

                try (Rows checkRows = conn.query(checkTableQuery)) {
                    tabelaExiste = checkRows.nextRow() != null;
                }

                if (!tabelaExiste) {
                    Log.e(TAG, "Tabela 'servicos' não existe no banco de dados");
                    mainHandler.post(() -> callback.onResult(servicos, true, "Nenhum serviço encontrado"));
                    return;
                }

                // Verifica se há serviços para este usuário
                String countQuery = String.format(
                        "SELECT COUNT(*) FROM servicos WHERE id_usuario = '%s'",
                        userId
                );

                int count = 0;
                try (Rows countRows = conn.query(countQuery)) {
                    Object[] countRow = countRows.nextRow();
                    if (countRow != null) {
                        count = ((Number) countRow[0]).intValue();
                    }
                }

                Log.d(TAG, "Contagem de serviços para o usuário " + userId + ": " + count);

                // Se não houver serviços, retorna lista vazia com sucesso
                if (count == 0) {
                    Log.i(TAG, "Nenhum serviço encontrado para o usuário: " + userId);
                    mainHandler.post(() -> callback.onResult(servicos, true, "Nenhum serviço encontrado"));
                    return;
                }

                // Se houver serviços, busca os dados
                String query = String.format(
                        "SELECT id_servico, id_usuario, nome, custo_unitario, valor_unitario " +
                                "FROM servicos WHERE id_usuario = '%s' ORDER BY nome",
                        userId
                );

                Log.d(TAG, "Executando query de serviços: " + query);

                try (Rows rows = conn.query(query)) {
                    Object[] row;
                    while ((row = rows.nextRow()) != null) {
                        try {
                            int idServico = ((Number) row[0]).intValue();
                            String idUsuario = (String) row[1];
                            String nome = (String) row[2];
                            double custoUnitario = ((Number) row[3]).doubleValue();
                            double valorUnitario = ((Number) row[4]).doubleValue();

                            Servico servico = new Servico(idServico, idUsuario, nome, custoUnitario, valorUnitario);
                            servicos.add(servico);
                            Log.d(TAG, "Serviço adicionado: ID=" + idServico + ", Nome=" + nome);
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao processar linha de serviço: " + e.getMessage(), e);
                            // Continua processando outras linhas
                        }
                    }
                }

                Log.i(TAG, "Serviços carregados com sucesso: " + servicos.size() + " registros");
                mainHandler.post(() -> callback.onResult(servicos, true, "Serviços carregados com sucesso"));
            } catch (Exception e) {
                String errorMsg = "Erro ao listar serviços: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(servicos, false, errorMsg));
            } finally {
                // Garantir que a conexão seja fechada
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao fechar conexão: " + e.getMessage());
                    }
                }
            }
        });
    }

    // --- MÉTODOS ASSÍNCRONOS PARA VENDAS ---

    /**
     * Lista todas as vendas do usuário autenticado de forma assíncrona.
     */
    public static void listarVendasAsync(VendaCallback<List<Venda>> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            String errorMsg = "Usuário não autenticado";
            Log.e(TAG, errorMsg);
            mainHandler.post(() -> callback.onResult(new ArrayList<>(), false, errorMsg));
            return;
        }

        if (!DatabaseManager.isInitialized()) {
            String errorMsg = "DatabaseManager não inicializado. Tente novamente mais tarde.";
            Log.w(TAG, errorMsg);
            mainHandler.post(() -> callback.onResult(new ArrayList<>(), false, errorMsg));
            return;
        }

        final String userId = user.getUid();
        Log.d(TAG, "Iniciando consulta de vendas para usuário: " + userId);

        executor.execute(() -> {
            List<Venda> vendas = new ArrayList<>();
            Connection conn = null;
            try {
                Database db = DatabaseManager.getDatabase();
                if (db == null) {
                    throw new IllegalStateException("DatabaseManager.getDatabase() retornou null apesar de inicializado.");
                }

                Log.d(TAG, "Obteve instância do banco de dados, executando query");

                conn = db.connect();
                // Primeiro, verifica se a tabela existe e tem dados
                String checkTableQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='vendas'";
                boolean tabelaExiste = false;

                try (Rows checkRows = conn.query(checkTableQuery)) {
                    tabelaExiste = checkRows.nextRow() != null;
                }

                if (!tabelaExiste) {
                    Log.e(TAG, "Tabela 'vendas' não existe no banco de dados");
                    mainHandler.post(() -> callback.onResult(vendas, true, "Nenhuma venda encontrada"));
                    return;
                }

                // Verifica se há vendas para este usuário
                String countQuery = String.format(
                        "SELECT COUNT(*) FROM vendas WHERE id_usuario = '%s'",
                        userId
                );

                int count = 0;
                try (Rows countRows = conn.query(countQuery)) {
                    Object[] countRow = countRows.nextRow();
                    if (countRow != null) {
                        count = ((Number) countRow[0]).intValue();
                    }
                }

                Log.d(TAG, "Contagem de vendas para o usuário " + userId + ": " + count);

                // Se não houver vendas, retorna lista vazia com sucesso
                if (count == 0) {
                    Log.i(TAG, "Nenhuma venda encontrada para o usuário: " + userId);
                    mainHandler.post(() -> callback.onResult(vendas, true, "Nenhuma venda encontrada"));
                    return;
                }

                // Se houver vendas, busca os dados
                String query = String.format(
                        "SELECT id_venda, id_usuario, tipo_item, id_servico_vendido, " +
                                "nome_item_vendido, valor_unitario_vendido, quantidade, valor_total_venda, " +
                                "data_hora_venda, metodo_pagamento " +
                                "FROM vendas WHERE id_usuario = '%s' " +
                                "ORDER BY data_hora_venda DESC",
                        userId
                );

                Log.d(TAG, "Executando query: " + query);

                try (Rows rows = conn.query(query)) {
                    Object[] row;
                    while ((row = rows.nextRow()) != null) {
                        try {
                            int id_venda = ((Number) row[0]).intValue();
                            String id_usuario_db = (String) row[1];
                            String tipo_item = (String) row[2];
                            Integer id_servico_vendido = (row[3] == null) ? null : ((Number) row[3]).intValue();
                            String nome_item_vendido = (String) row[4];
                            double valor_unitario_vendido = ((Number) row[5]).doubleValue();
                            int quantidade = ((Number) row[6]).intValue();
                            double valor_total_venda = ((Number) row[7]).doubleValue();
                            String data_hora_venda = (String) row[8];
                            String metodo_pagamento = (String) row[9];

                            Venda venda = new Venda(
                                    id_venda, id_usuario_db, tipo_item, id_servico_vendido,
                                    nome_item_vendido, valor_unitario_vendido, quantidade,
                                    valor_total_venda, data_hora_venda, metodo_pagamento
                            );
                            vendas.add(venda);
                            Log.d(TAG, "Venda adicionada: ID=" + id_venda + ", Item=" + nome_item_vendido);
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao processar linha de venda: " + e.getMessage(), e);
                            // Continua processando outras linhas
                        }
                    }
                }

                Log.i(TAG, "Vendas carregadas com sucesso: " + vendas.size() + " registros");
                mainHandler.post(() -> callback.onResult(vendas, true, "Vendas carregadas com sucesso"));
            } catch (Exception e) {
                String errorMsg = "Erro ao listar vendas: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                mainHandler.post(() -> callback.onResult(vendas, false, errorMsg));
            } finally {
                // Garantir que a conexão seja fechada
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao fechar conexão: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Insere uma nova venda no banco de dados de forma assíncrona.
     */
    public static void inserirVendaAsync(final Venda venda, VendaCallback<Boolean> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            mainHandler.post(() -> callback.onResult(false, false, "Usuário não autenticado"));
            return;
        }
        if (!DatabaseManager.isInitialized()) {
            mainHandler.post(() -> callback.onResult(false, false, "DatabaseManager não inicializado. Tente novamente mais tarde."));
            return;
        }

        final String userId = user.getUid();
        venda.setId_usuario(userId);
        if (venda.getData_hora_venda() == null || venda.getData_hora_venda().isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            venda.setData_hora_venda(sdf.format(new Date()));
        }

        executor.execute(() -> {
            Connection conn = null;
            boolean success = false;
            String message = "";
            try {
                Database db = DatabaseManager.getDatabase();
                if (db == null) {
                    throw new IllegalStateException("DatabaseManager.getDatabase() retornou null apesar de inicializado.");
                }
                conn = db.connect();
                conn.execute("BEGIN TRANSACTION"); // Inicia a transação

                if ("produto".equals(venda.getTipo_item())) {
                    String checkEstoqueQuery = String.format(
                            "SELECT quantidade FROM estoque " +
                                    "WHERE id_usuario = '%s' AND nome_produto = '%s' AND quantidade >= %d",
                            userId,
                            venda.getNome_item_vendido().replace("'", "''"),
                            venda.getQuantidade()
                    );
                    boolean estoqueSuficiente = false;
                    try (Rows rows = conn.query(checkEstoqueQuery)) {
                        if (rows.nextRow() != null) {
                            estoqueSuficiente = true;
                        }
                    }
                    if (!estoqueSuficiente) {
                        message = "Estoque insuficiente para realizar a venda";
                        Log.w(TAG, message);
                        conn.execute("ROLLBACK");
                        String finalMessage1 = message;
                        mainHandler.post(() -> callback.onResult(false, false, finalMessage1));
                        return;
                    }
                    String updateEstoqueQuery = String.format(
                            "UPDATE estoque SET quantidade = quantidade - %d " +
                                    "WHERE id_usuario = '%s' AND nome_produto = '%s'",
                            venda.getQuantidade(),
                            userId,
                            venda.getNome_item_vendido().replace("'", "''")
                    );
                    conn.execute(updateEstoqueQuery);
                }

                String insertQuery;
                if (venda.getId_servico_vendido() == null) {
                    insertQuery = String.format(
                            "INSERT INTO vendas (id_usuario, tipo_item, id_servico_vendido, nome_item_vendido, " +
                                    "valor_unitario_vendido, quantidade, valor_total_venda, data_hora_venda, metodo_pagamento) " +
                                    "VALUES ('%s', '%s', NULL, '%s', %f, %d, %f, '%s', '%s')",
                            userId,
                            venda.getTipo_item(),
                            venda.getNome_item_vendido().replace("'", "''"),
                            venda.getValor_unitario_vendido(),
                            venda.getQuantidade(),
                            venda.getValor_total_venda(),
                            venda.getData_hora_venda(),
                            venda.getMetodo_pagamento().replace("'", "''")
                    );
                } else {
                    insertQuery = String.format(
                            "INSERT INTO vendas (id_usuario, tipo_item, id_servico_vendido, nome_item_vendido, " +
                                    "valor_unitario_vendido, quantidade, valor_total_venda, data_hora_venda, metodo_pagamento) " +
                                    "VALUES ('%s', '%s', %d, '%s', %f, %d, %f, '%s', '%s')",
                            userId,
                            venda.getTipo_item(),
                            venda.getId_servico_vendido(),
                            venda.getNome_item_vendido().replace("'", "''"),
                            venda.getValor_unitario_vendido(),
                            venda.getQuantidade(),
                            venda.getValor_total_venda(),
                            venda.getData_hora_venda(),
                            venda.getMetodo_pagamento().replace("'", "''")
                    );
                }
                conn.execute(insertQuery);

                try (Rows rowsId = conn.query("SELECT last_insert_rowid()")) {
                    Object[] rowIdData = rowsId.nextRow();
                    if (rowIdData != null) {
                        venda.setId_venda(((Number) rowIdData[0]).intValue());
                        success = true;
                        message = "Venda inserida com sucesso";
                        conn.execute("COMMIT");
                    } else {
                        success = false;
                        message = "Não foi possível obter o ID da venda";
                        conn.execute("ROLLBACK");
                    }
                }
            } catch (Exception e) {
                success = false;
                message = "Erro ao inserir venda: " + e.getMessage();
                Log.e(TAG, message, e);
                if (conn != null) {
                    try { conn.execute("ROLLBACK"); } catch (Exception re) { Log.e(TAG, "Erro no rollback", re); }
                }
            } finally {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ce) { Log.e(TAG, "Erro ao fechar conexão", ce); }
                }
                final boolean finalSuccess = success;
                final String finalMessage = message;
                mainHandler.post(() -> callback.onResult(finalSuccess, finalSuccess, finalMessage));
            }
        });
    }

    /**
     * Atualiza uma venda existente de forma assíncrona.
     */
    public static void atualizarVendaAsync(final Venda venda, VendaCallback<Boolean> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            mainHandler.post(() -> callback.onResult(false, false, "Usuário não autenticado"));
            return;
        }
        if (!DatabaseManager.isInitialized()) {
            mainHandler.post(() -> callback.onResult(false, false, "DatabaseManager não inicializado. Tente novamente mais tarde."));
            return;
        }

        final String userId = user.getUid();
        Log.d(TAG, "Iniciando atualização da venda ID: " + venda.getId_venda());

        executor.execute(() -> {
            Connection conn = null;
            boolean success = false;
            String message = "";
            try {
                Database db = DatabaseManager.getDatabase();
                if (db == null) {
                    throw new IllegalStateException("DatabaseManager.getDatabase() retornou null apesar de inicializado.");
                }
                conn = db.connect();

                // Inicia a transação antes de qualquer operação
                conn.execute("BEGIN TRANSACTION");

                // Recupera a venda original para comparar e ajustar o estoque, se necessário.
                Venda vendaOriginal = null;
                String getVendaQuery = String.format(
                        "SELECT tipo_item, nome_item_vendido, quantidade FROM vendas " +
                                "WHERE id_venda = %d AND id_usuario = '%s'",
                        venda.getId_venda(), userId
                );

                Log.d(TAG, "Verificando venda original: " + getVendaQuery);

                try (Rows rows = conn.query(getVendaQuery)) {
                    Object[] rowData = rows.nextRow();
                    if (rowData != null) {
                        vendaOriginal = new Venda();
                        vendaOriginal.setTipo_item((String) rowData[0]);
                        vendaOriginal.setNome_item_vendido((String) rowData[1]);
                        vendaOriginal.setQuantidade(((Number) rowData[2]).intValue());
                        Log.d(TAG, "Venda original encontrada: " + vendaOriginal.getNome_item_vendido() +
                                ", Tipo: " + vendaOriginal.getTipo_item() +
                                ", Qtd: " + vendaOriginal.getQuantidade());
                    }
                }

                if (vendaOriginal == null) {
                    message = "Venda não encontrada para atualização";
                    Log.w(TAG, message);
                    conn.execute("ROLLBACK");
                    String finalMessage1 = message;
                    mainHandler.post(() -> callback.onResult(false, false, finalMessage1));
                    return;
                }

                boolean estoqueOk = true;
                // Lógica de ajuste de estoque
                if ("produto".equals(vendaOriginal.getTipo_item()) && "produto".equals(venda.getTipo_item())) {
                    int diferencaQuantidade = venda.getQuantidade() - vendaOriginal.getQuantidade();
                    Log.d(TAG, "Diferença de quantidade: " + diferencaQuantidade);

                    if (diferencaQuantidade > 0) {
                        String checkEstoqueQuery = String.format(
                                "SELECT quantidade FROM estoque WHERE id_usuario = '%s' AND nome_produto = '%s' AND quantidade >= %d",
                                userId, venda.getNome_item_vendido().replace("'", "''"), diferencaQuantidade
                        );
                        Log.d(TAG, "Verificando estoque disponível: " + checkEstoqueQuery);

                        try (Rows rowsCheck = conn.query(checkEstoqueQuery)) {
                            if (rowsCheck.nextRow() == null) {
                                estoqueOk = false;
                                Log.w(TAG, "Estoque insuficiente para atualização");
                            }
                        }
                    }

                    if (estoqueOk && diferencaQuantidade != 0) {
                        String updateEstoqueQuery = String.format(
                                "UPDATE estoque SET quantidade = quantidade - %d WHERE id_usuario = '%s' AND nome_produto = '%s'",
                                diferencaQuantidade, userId, venda.getNome_item_vendido().replace("'", "''")
                        );
                        Log.d(TAG, "Atualizando estoque: " + updateEstoqueQuery);
                        conn.execute(updateEstoqueQuery);
                    }
                } else if ("produto".equals(vendaOriginal.getTipo_item()) && !"produto".equals(venda.getTipo_item())) {
                    // Produto -> Serviço: devolver ao estoque
                    String updateEstoqueQuery = String.format(
                            "UPDATE estoque SET quantidade = quantidade + %d WHERE id_usuario = '%s' AND nome_produto = '%s'",
                            vendaOriginal.getQuantidade(), userId, vendaOriginal.getNome_item_vendido().replace("'", "''")
                    );
                    Log.d(TAG, "Devolvendo ao estoque (mudança para serviço): " + updateEstoqueQuery);
                    conn.execute(updateEstoqueQuery);
                } else if (!"produto".equals(vendaOriginal.getTipo_item()) && "produto".equals(venda.getTipo_item())) {
                    // Serviço -> Produto: verificar estoque
                    String checkEstoqueQuery = String.format(
                            "SELECT quantidade FROM estoque WHERE id_usuario = '%s' AND nome_produto = '%s' AND quantidade >= %d",
                            userId, venda.getNome_item_vendido().replace("'", "''"), venda.getQuantidade()
                    );
                    Log.d(TAG, "Verificando estoque (mudança de serviço para produto): " + checkEstoqueQuery);

                    try (Rows rowsCheck = conn.query(checkEstoqueQuery)) {
                        if (rowsCheck.nextRow() == null) {
                            estoqueOk = false;
                            Log.w(TAG, "Estoque insuficiente para mudança de serviço para produto");
                        }
                    }

                    if (estoqueOk) {
                        String updateEstoqueQuery = String.format(
                                "UPDATE estoque SET quantidade = quantidade - %d WHERE id_usuario = '%s' AND nome_produto = '%s'",
                                venda.getQuantidade(), userId, venda.getNome_item_vendido().replace("'", "''")
                        );
                        Log.d(TAG, "Atualizando estoque (mudança de serviço para produto): " + updateEstoqueQuery);
                        conn.execute(updateEstoqueQuery);
                    }
                }

                if (!estoqueOk) {
                    message = "Estoque insuficiente para atualizar a venda";
                    Log.w(TAG, message);
                    conn.execute("ROLLBACK");
                    String finalMessage1 = message;
                    mainHandler.post(() -> callback.onResult(false, false, finalMessage1));
                    return;
                }

                // Atualiza a venda
                String updateQuery;
                if (venda.getId_servico_vendido() == null) {
                    updateQuery = String.format(
                            "UPDATE vendas SET " +
                                    "tipo_item = '%s', " +
                                    "id_servico_vendido = NULL, " +
                                    "nome_item_vendido = '%s', " +
                                    "valor_unitario_vendido = %f, " +
                                    "quantidade = %d, " +
                                    "valor_total_venda = %f, " +
                                    "data_hora_venda = '%s', " +
                                    "metodo_pagamento = '%s' " +
                                    "WHERE id_venda = %d AND id_usuario = '%s'",
                            venda.getTipo_item(),
                            venda.getNome_item_vendido().replace("'", "''"),
                            venda.getValor_unitario_vendido(),
                            venda.getQuantidade(),
                            venda.getValor_total_venda(),
                            venda.getData_hora_venda(),
                            venda.getMetodo_pagamento().replace("'", "''"),
                            venda.getId_venda(),
                            userId
                    );
                } else {
                    updateQuery = String.format(
                            "UPDATE vendas SET " +
                                    "tipo_item = '%s', " +
                                    "id_servico_vendido = %d, " +
                                    "nome_item_vendido = '%s', " +
                                    "valor_unitario_vendido = %f, " +
                                    "quantidade = %d, " +
                                    "valor_total_venda = %f, " +
                                    "data_hora_venda = '%s', " +
                                    "metodo_pagamento = '%s' " +
                                    "WHERE id_venda = %d AND id_usuario = '%s'",
                            venda.getTipo_item(),
                            venda.getId_servico_vendido(),
                            venda.getNome_item_vendido().replace("'", "''"),
                            venda.getValor_unitario_vendido(),
                            venda.getQuantidade(),
                            venda.getValor_total_venda(),
                            venda.getData_hora_venda(),
                            venda.getMetodo_pagamento().replace("'", "''"),
                            venda.getId_venda(),
                            userId
                    );
                }

                Log.d(TAG, "Executando atualização: " + updateQuery);
                conn.execute(updateQuery);

                // Verifica se a atualização foi bem-sucedida
                String checkQuery = String.format(
                        "SELECT COUNT(*) FROM vendas WHERE id_venda = %d AND id_usuario = '%s'",
                        venda.getId_venda(), userId
                );
                try (Rows rows = conn.query(checkQuery)) {
                    Object[] rowData = rows.nextRow();
                    if (rowData != null && ((Number) rowData[0]).intValue() > 0) {
                        success = true;
                        message = "Venda atualizada com sucesso";
                        conn.execute("COMMIT");
                    } else {
                        success = false;
                        message = "Falha ao atualizar venda";
                        conn.execute("ROLLBACK");
                    }
                }
            } catch (Exception e) {
                success = false;
                message = "Erro ao atualizar venda: " + e.getMessage();
                Log.e(TAG, message, e);
                if (conn != null) {
                    try { conn.execute("ROLLBACK"); } catch (Exception re) { Log.e(TAG, "Erro no rollback", re); }
                }
            } finally {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ce) { Log.e(TAG, "Erro ao fechar conexão", ce); }
                }
                final boolean finalSuccess = success;
                final String finalMessage = message;
                mainHandler.post(() -> callback.onResult(finalSuccess, finalSuccess, finalMessage));
            }
        });
    }

    /**
     * Exclui uma venda do banco de dados de forma assíncrona.
     */
    public static void excluirVendaAsync(final int idVenda, VendaCallback<Boolean> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            mainHandler.post(() -> callback.onResult(false, false, "Usuário não autenticado"));
            return;
        }
        if (!DatabaseManager.isInitialized()) {
            mainHandler.post(() -> callback.onResult(false, false, "DatabaseManager não inicializado. Tente novamente mais tarde."));
            return;
        }

        final String userId = user.getUid();
        Log.d(TAG, "Iniciando exclusão da venda ID: " + idVenda);

        executor.execute(() -> {
            Connection conn = null;
            boolean success = false;
            String message = "";
            try {
                Database db = DatabaseManager.getDatabase();
                if (db == null) {
                    throw new IllegalStateException("DatabaseManager.getDatabase() retornou null apesar de inicializado.");
                }
                conn = db.connect();
                conn.execute("BEGIN TRANSACTION");

                // Primeiro, recupera informações da venda para possível ajuste de estoque
                Venda venda = null;
                String getVendaQuery = String.format(
                        "SELECT tipo_item, nome_item_vendido, quantidade FROM vendas " +
                                "WHERE id_venda = %d AND id_usuario = '%s'",
                        idVenda, userId
                );
                try (Rows rows = conn.query(getVendaQuery)) {
                    Object[] rowData = rows.nextRow();
                    if (rowData != null) {
                        venda = new Venda();
                        venda.setTipo_item((String) rowData[0]);
                        venda.setNome_item_vendido((String) rowData[1]);
                        venda.setQuantidade(((Number) rowData[2]).intValue());
                    }
                }

                if (venda == null) {
                    message = "Venda não encontrada para exclusão";
                    Log.w(TAG, message);
                    conn.execute("ROLLBACK");
                    String finalMessage1 = message;
                    mainHandler.post(() -> callback.onResult(false, false, finalMessage1));
                    return;
                }

                // Se for produto, devolve ao estoque
                if ("produto".equals(venda.getTipo_item())) {
                    String updateEstoqueQuery = String.format(
                            "UPDATE estoque SET quantidade = quantidade + %d " +
                                    "WHERE id_usuario = '%s' AND nome_produto = '%s'",
                            venda.getQuantidade(),
                            userId,
                            venda.getNome_item_vendido().replace("'", "''")
                    );
                    Log.d(TAG, "Devolvendo ao estoque: " + updateEstoqueQuery);
                    conn.execute(updateEstoqueQuery);
                }

                // Exclui a venda
                String deleteQuery = String.format(
                        "DELETE FROM vendas WHERE id_venda = %d AND id_usuario = '%s'",
                        idVenda, userId
                );
                Log.d(TAG, "Executando exclusão: " + deleteQuery);
                conn.execute(deleteQuery);

                // Verifica se a exclusão foi bem-sucedida
                String checkQuery = String.format(
                        "SELECT COUNT(*) FROM vendas WHERE id_venda = %d AND id_usuario = '%s'",
                        idVenda, userId
                );
                try (Rows rows = conn.query(checkQuery)) {
                    Object[] rowData = rows.nextRow();
                    if (rowData != null && ((Number) rowData[0]).intValue() == 0) {
                        success = true;
                        message = "Venda excluída com sucesso";
                        conn.execute("COMMIT");
                    } else {
                        success = false;
                        message = "Falha ao excluir venda";
                        conn.execute("ROLLBACK");
                    }
                }
            } catch (Exception e) {
                success = false;
                message = "Erro ao excluir venda: " + e.getMessage();
                Log.e(TAG, message, e);
                if (conn != null) {
                    try { conn.execute("ROLLBACK"); } catch (Exception re) { Log.e(TAG, "Erro no rollback", re); }
                }
            } finally {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ce) { Log.e(TAG, "Erro ao fechar conexão", ce); }
                }
                final boolean finalSuccess = success;
                final String finalMessage = message;
                mainHandler.post(() -> callback.onResult(finalSuccess, finalSuccess, finalMessage));
            }
        });
    }
}
