package tech.turso.SyncroManage;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import tech.turso.libsql.Connection;
import tech.turso.libsql.Database;
import tech.turso.libsql.Rows;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Data Access Object para a tela Home, contendo métodos específicos
 * para obter dados resumidos do banco para apresentação ao usuário.
 */
public class HomeDAO {
    private static final String TAG = "HomeDAO";

    // Classe modelo para resumo financeiro
    public static class ResumoFinanceiro {
        public final double vendasHoje;
        public final double vendasMes;
        public final double receitaHoje;
        public final double receitaMes;

        public ResumoFinanceiro(double vendasHoje, double vendasMes, double receitaHoje, double receitaMes) {
            this.vendasHoje = vendasHoje;
            this.vendasMes = vendasMes;
            this.receitaHoje = receitaHoje;
            this.receitaMes = receitaMes;
        }
    }

    // Classe modelo para produtos/serviços em alta
    public static class ItemEmAlta {
        public final String nome;
        public final String tipo;
        public final int quantidade;
        public final double valorTotal;

        public ItemEmAlta(String nome, String tipo, int quantidade, double valorTotal) {
            this.nome = nome;
            this.tipo = tipo;
            this.quantidade = quantidade;
            this.valorTotal = valorTotal;
        }
    }

    // Classe modelo para produtos com estoque baixo
    public static class EstoqueBaixo {
        public final int idEstoque;
        public final String nome;
        public final int quantidade;
        public final double valorUnitario;

        public EstoqueBaixo(int idEstoque, String nome, int quantidade, double valorUnitario) {
            this.idEstoque = idEstoque;
            this.nome = nome;
            this.quantidade = quantidade;
            this.valorUnitario = valorUnitario;
        }
    }

    /**
     * Interface para callback de resultados assíncronos
     */
    public interface HomeDataCallback<T> {
        void onResult(T data, boolean success, String message);
    }

    /**
     * Obtém o resumo financeiro: vendas do dia e do mês
     */
    public static void getResumoFinanceiro(HomeDataCallback<ResumoFinanceiro> callback) {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                callback.onResult(new ResumoFinanceiro(0, 0, 0, 0), false, "Usuário não autenticado");
                return;
            }

            String userId = currentUser.getUid();
            Database db = DatabaseManager.getDatabase();

            // Executa em uma thread separada
            new Thread(() -> {
                try (Connection conn = db.connect()) {
                    // Data de hoje e primeiro dia do mês para filtros
                    String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    String primeiroDiaMes = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

                    // Consultar vendas de hoje - Concatenação direta dos parâmetros na string SQL
                    String queryHoje = "SELECT COUNT(*) as total_vendas, SUM(valor_total_venda) as receita " +
                            "FROM vendas WHERE id_usuario = '" + userId + "' AND date(data_hora_venda) = '" + dataHoje + "'";

                    double vendasHoje = 0;
                    double receitaHoje = 0;

                    try (Rows rowsHoje = conn.query(queryHoje)) {
                        Object[] row = rowsHoje.nextRow();
                        if (row != null) {
                            vendasHoje = row[0] != null ? Double.parseDouble(row[0].toString()) : 0;
                            receitaHoje = row[1] != null ? Double.parseDouble(row[1].toString()) : 0;
                        }
                    }

                    // Consultar vendas do mês - Concatenação direta dos parâmetros na string SQL
                    String queryMes = "SELECT COUNT(*) as total_vendas, SUM(valor_total_venda) as receita " +
                            "FROM vendas WHERE id_usuario = '" + userId + "' AND date(data_hora_venda) >= '" + primeiroDiaMes + "'";

                    double vendasMes = 0;
                    double receitaMes = 0;

                    try (Rows rowsMes = conn.query(queryMes)) {
                        Object[] row = rowsMes.nextRow();
                        if (row != null) {
                            vendasMes = row[0] != null ? Double.parseDouble(row[0].toString()) : 0;
                            receitaMes = row[1] != null ? Double.parseDouble(row[1].toString()) : 0;
                        }
                    }

                    ResumoFinanceiro resumo = new ResumoFinanceiro(vendasHoje, vendasMes, receitaHoje, receitaMes);
                    // Retorna para thread principal
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onResult(resumo, true, "Sucesso"));

                } catch (Exception e) {
                    Log.e(TAG, "Erro ao obter resumo financeiro", e);
                    // Retorna erro para thread principal
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onResult(new ResumoFinanceiro(0, 0, 0, 0), false, e.getMessage()));
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao preparar query de resumo financeiro", e);
            callback.onResult(new ResumoFinanceiro(0, 0, 0, 0), false, e.getMessage());
        }
    }

    /**
     * Obtém os itens (produtos ou serviços) mais vendidos recentemente
     */
    public static void getItensEmAlta(HomeDataCallback<List<ItemEmAlta>> callback) {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                callback.onResult(new ArrayList<>(), false, "Usuário não autenticado");
                return;
            }

            String userId = currentUser.getUid();
            Database db = DatabaseManager.getDatabase();

            // Executa em uma thread separada
            new Thread(() -> {
                try (Connection conn = db.connect()) {
                    // Consulta os 5 produtos mais vendidos nos últimos 30 dias
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_MONTH, -30);
                    String data30DiasAtras = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

                    // Concatenação direta dos parâmetros na string SQL
                    String query = "SELECT nome_item_vendido, tipo_item, SUM(quantidade) as qtd_total, " +
                            "SUM(valor_total_venda) as valor_total FROM vendas " +
                            "WHERE id_usuario = '" + userId + "' AND data_hora_venda >= '" + data30DiasAtras + "' " +
                            "GROUP BY nome_item_vendido, tipo_item " +
                            "ORDER BY qtd_total DESC LIMIT 5";

                    List<ItemEmAlta> itensEmAlta = new ArrayList<>();

                    try (Rows rows = conn.query(query)) {
                        Object[] row;
                        while ((row = rows.nextRow()) != null) {
                            String nome = row[0] != null ? row[0].toString() : "";
                            String tipo = row[1] != null ? row[1].toString() : "";
                            int quantidade = row[2] != null ? Integer.parseInt(row[2].toString()) : 0;
                            double valorTotal = row[3] != null ? Double.parseDouble(row[3].toString()) : 0;

                            itensEmAlta.add(new ItemEmAlta(nome, tipo, quantidade, valorTotal));
                        }
                    }

                    // Retorna para thread principal
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onResult(itensEmAlta, true, "Sucesso"));

                } catch (Exception e) {
                    Log.e(TAG, "Erro ao obter itens em alta", e);
                    // Retorna erro para thread principal
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onResult(new ArrayList<>(), false, e.getMessage()));
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao preparar query de itens em alta", e);
            callback.onResult(new ArrayList<>(), false, e.getMessage());
        }
    }

    /**
     * Obtém os produtos com estoque baixo (menos de 5 unidades)
     */
    public static void getProdutosEstoqueBaixo(HomeDataCallback<List<EstoqueBaixo>> callback) {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                callback.onResult(new ArrayList<>(), false, "Usuário não autenticado");
                return;
            }

            String userId = currentUser.getUid();
            Database db = DatabaseManager.getDatabase();

            // Executa em uma thread separada
            new Thread(() -> {
                try (Connection conn = db.connect()) {
                    // Consulta produtos com menos de 5 unidades em estoque
                    // Concatenação direta dos parâmetros na string SQL
                    String query = "SELECT id_estoque, nome_produto, quantidade, valor_unitario " +
                            "FROM estoque WHERE id_usuario = '" + userId + "' AND quantidade < 5 " +
                            "ORDER BY quantidade ASC";

                    List<EstoqueBaixo> produtosEstoqueBaixo = new ArrayList<>();

                    try (Rows rows = conn.query(query)) {
                        Object[] row;
                        while ((row = rows.nextRow()) != null) {
                            int idEstoque = row[0] != null ? Integer.parseInt(row[0].toString()) : 0;
                            String nome = row[1] != null ? row[1].toString() : "";
                            int quantidade = row[2] != null ? Integer.parseInt(row[2].toString()) : 0;
                            double valorUnitario = row[3] != null ? Double.parseDouble(row[3].toString()) : 0;

                            produtosEstoqueBaixo.add(new EstoqueBaixo(idEstoque, nome, quantidade, valorUnitario));
                        }
                    }

                    // Retorna para thread principal
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onResult(produtosEstoqueBaixo, true, "Sucesso"));

                } catch (Exception e) {
                    Log.e(TAG, "Erro ao obter produtos com estoque baixo", e);
                    // Retorna erro para thread principal
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onResult(new ArrayList<>(), false, e.getMessage()));
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao preparar query de produtos com estoque baixo", e);
            callback.onResult(new ArrayList<>(), false, e.getMessage());
        }
    }
}