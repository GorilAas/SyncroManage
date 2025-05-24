package tech.turso.SyncroManage;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Imports para iText 7
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfViewerPreferences;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import tech.turso.libsql.Connection;
import tech.turso.libsql.Database;
import tech.turso.libsql.Rows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Classe DAO para operações relacionadas a relatórios.
 * Fornece métodos para geração de relatórios de vendas e exportação em diferentes formatos.
 */
public class RelatorioDAO {
    private static final String TAG = "RelatorioDAO";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Interface para callbacks de operações de relatório.
     */
    public interface RelatorioCallback<T> {
        void onResult(T data, boolean success, String message);
    }

    /**
     * Obtém relatório de vendas agrupado por período.
     *
     * @param dataInicio Data inicial no formato YYYY-MM-DD
     * @param dataFim Data final no formato YYYY-MM-DD
     * @return Lista de arrays de strings com os dados do relatório
     */
    public static List<String[]> getRelatorioVendasAgrupado(String dataInicio, String dataFim) {
        List<String[]> resultado = new ArrayList<>();

        // Verifica se o usuário está autenticado
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Usuário não autenticado");
            return criarRelatorioVazio();
        }

        // Verifica se o banco de dados está inicializado
        if (!DatabaseManager.isInitialized()) {
            Log.e(TAG, "DatabaseManager não inicializado");
            return criarRelatorioVazio();
        }

        // Obtém a instância do banco de dados
        Database db = DatabaseManager.getDatabase();

        try (Connection conn = db.connect()) {
            // Query: retorna os dados agrupados de vendas no período
            String query = String.format(
                    "SELECT strftime('%%m/%%Y', v.data_hora_venda) AS mes, " +
                            "v.tipo_item AS tipo, " +
                            "v.nome_item_vendido AS item, " +
                            "SUM(v.quantidade) AS totalQuantidade, " +
                            "SUM(v.valor_total_venda) AS totalValor, " +
                            "v.metodo_pagamento AS forma_pagamento, " +
                            "COALESCE((SELECT custo_unitario FROM estoque e WHERE e.nome_produto = v.nome_item_vendido LIMIT 1), " +
                            "         (SELECT custo_unitario FROM servicos s WHERE s.nome = v.nome_item_vendido LIMIT 1), 0) AS custo_unitario " +
                            "FROM vendas v " +
                            "WHERE v.data_hora_venda BETWEEN '%s' AND '%s' " +
                            "AND v.id_usuario = '%s' " +
                            "GROUP BY mes, v.tipo_item, v.nome_item_vendido, v.metodo_pagamento " +
                            "ORDER BY mes ASC",
                    dataInicio, dataFim, user.getUid()
            );

            try (Rows rows = conn.query(query)) {
                boolean dadosEncontrados = false;
                Object[] row;
                while ((row = rows.nextRow()) != null) {
                    dadosEncontrados = true;
                    String mes = row[0] != null ? row[0].toString() : "";
                    String tipo = row[1] != null ? row[1].toString() : "";
                    String item = row[2] != null ? row[2].toString() : "";
                    String totalQtd = row[3] != null ? row[3].toString() : "0";
                    String totalValor = row[4] != null ? row[4].toString() : "0";
                    String formaPagamento = row[5] != null ? row[5].toString() : "";
                    String custoUnit = row[6] != null ? row[6].toString() : "0";

                    double qtd = 0, totVal = 0, custoUn = 0;
                    try {
                        qtd = Double.parseDouble(totalQtd);
                        totVal = Double.parseDouble(totalValor);
                        custoUn = Double.parseDouble(custoUnit);
                    } catch(Exception ex) {
                        Log.w(TAG, "Erro ao converter valores numéricos: " + ex.getMessage());
                    }

                    double lucro = totVal - (qtd * custoUn);
                    String lucroStr = String.format("%.2f", lucro);

                    // Array com 8 campos
                    String[] linha = new String[] { mes, tipo, item, totalQtd, totalValor, formaPagamento, custoUnit, lucroStr };
                    resultado.add(linha);
                }

                // Se não encontrou nenhum dado, criar um relatório vazio
                if (!dadosEncontrados) {
                    Log.i(TAG, "Nenhum dado encontrado para o período. Criando registros zerados para relatório.");
                    return criarRelatorioVazio();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao consultar vendas agrupadas: " + e.getMessage(), e);
            return criarRelatorioVazio();
        }

        return resultado;
    }

    /**
     * Obtém relatório de vendas agrupado por período de forma assíncrona.
     *
     * @param dataInicio Data inicial no formato YYYY-MM-DD
     * @param dataFim Data final no formato YYYY-MM-DD
     * @param callback Callback para retornar os resultados
     */
    public static void getRelatorioVendasAgrupadoAsync(String dataInicio, String dataFim, RelatorioCallback<List<String[]>> callback) {
        executor.execute(() -> {
            List<String[]> resultado = getRelatorioVendasAgrupado(dataInicio, dataFim);
            mainHandler.post(() -> callback.onResult(resultado, true, "Relatório gerado com sucesso"));
        });
    }

    /**
     * Cria um relatório vazio para casos onde não há dados ou ocorreu erro.
     *
     * @return Lista com duas linhas representando um produto e um serviço zerados
     */
    private static List<String[]> criarRelatorioVazio() {
        List<String[]> resultado = new ArrayList<>();

        // Obtenha o mês atual para exibir no relatório vazio
        SimpleDateFormat formatoMes = new SimpleDateFormat("MM/yyyy");
        String mesAtual = formatoMes.format(new Date());

        // Crie duas linhas representando um produto e um serviço zerados
        // para permitir que o relatório seja gerado
        String[] linhaProduto = new String[] {
                mesAtual,        // Mês atual
                "produto",       // Tipo
                "Sem dados",     // Item
                "0",             // Quantidade
                "0.00",          // Total Valor
                "N/A",           // Método de pagamento
                "0.00",          // Custo unitário
                "0.00"           // Lucro
        };

        String[] linhaServico = new String[] {
                mesAtual,        // Mês atual
                "servico",       // Tipo
                "Sem dados",     // Item
                "0",             // Quantidade
                "0.00",          // Total Valor
                "N/A",           // Método de pagamento
                "0.00",          // Custo unitário
                "0.00"           // Lucro
        };

        resultado.add(linhaProduto);
        resultado.add(linhaServico);

        return resultado;
    }

    /**
     * Exporta os dados do relatório para um arquivo Excel.
     * Essa implementação gera um arquivo XML Spreadsheet (formato aceito pelo Excel).
     *
     * @param context Contexto da aplicação
     * @param dados Lista de arrays de strings com os dados do relatório
     * @return Arquivo Excel gerado ou null em caso de erro
     */
    public static File exportarExcel(Context context, List<String[]> dados) {
        File diretorio = context.getExternalFilesDir(null);
        // Usaremos a extensão .xml para indicar o formato de planilha
        File arquivoExcel = new File(diretorio, "relatorio_vendas.xml");

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>\n");
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n");
        sb.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n");
        sb.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n");
        sb.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n");
        sb.append("<Worksheet ss:Name=\"Relatorio\">\n");
        sb.append("<Table>\n");

        // Cabeçalho
        String[] headers = {"Mês", "Tipo", "Item", "Quantidade", "Total Valor", "Método", "Custo Unitário", "Lucro"};
        sb.append("<Row>\n");
        for (String header : headers) {
            sb.append("<Cell><Data ss:Type=\"String\">").append(header).append("</Data></Cell>\n");
        }
        sb.append("</Row>\n");

        // Linhas de dados
        for (String[] linha : dados) {
            sb.append("<Row>\n");
            for (String valor : linha) {
                sb.append("<Cell><Data ss:Type=\"String\">").append(valor != null ? valor : "").append("</Data></Cell>\n");
            }
            sb.append("</Row>\n");
        }

        sb.append("</Table>\n");
        sb.append("</Worksheet>\n");
        sb.append("</Workbook>\n");

        try (FileWriter writer = new FileWriter(arquivoExcel)) {
            writer.write(sb.toString());
            writer.flush();
            Log.i(TAG, "Arquivo Excel gerado com sucesso!");
            return arquivoExcel;
        } catch (IOException e) {
            Log.e(TAG, "Erro ao gerar arquivo Excel", e);
            Toast.makeText(context, "Erro ao gerar Excel: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Exporta os dados do relatório para um arquivo Excel de forma assíncrona.
     *
     * @param context Contexto da aplicação
     * @param dados Lista de arrays de strings com os dados do relatório
     * @param callback Callback para retornar o resultado
     */
    public static void exportarExcelAsync(Context context, List<String[]> dados, RelatorioCallback<File> callback) {
        executor.execute(() -> {
            File arquivo = exportarExcel(context, dados);
            mainHandler.post(() -> callback.onResult(arquivo, arquivo != null,
                    arquivo != null ? "Excel gerado com sucesso" : "Erro ao gerar Excel"));
        });
    }

    /**
     * Método para exportar os dados do relatório para um arquivo PDF usando iText 7.
     * O PDF apresenta uma tabela com 8 colunas e, ao final, exibe os totais bruto e líquido.
     *
     * @param context Contexto da aplicação
     * @param dados Lista de arrays de strings com os dados do relatório
     * @param nome Nome do usuário/empresa
     * @param documento CPF/CNPJ do usuário/empresa
     * @param empresa Nome da empresa
     * @return Arquivo PDF gerado ou null em caso de erro
     */
    public static File exportarPDF(Context context, List<String[]> dados, String nome, String documento, String empresa) {
        File diretorio = context.getExternalFilesDir(null);
        File arquivoPDF = new File(diretorio, "relatorio_vendas.pdf");

        try {
            PdfWriter writer = new PdfWriter(new FileOutputStream(arquivoPDF));
            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setTagged();
            pdfDoc.getCatalog().setLang(new PdfString("pt-BR"));
            pdfDoc.getCatalog().setViewerPreferences(new PdfViewerPreferences().setDisplayDocTitle(true));

            PdfDocumentInfo info = pdfDoc.getDocumentInfo();
            info.setTitle("Relatório de Vendas - SyncroManage");
            info.setAuthor("SyncroManage");
            info.setSubject("Relatório de Vendas");

            Document document = new Document(pdfDoc);
            PdfFont fontTitulo = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontNormal = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // Cabeçalho do documento
            Paragraph titulo = new Paragraph("Relatório de Vendas Agrupado")
                    .setFont(fontTitulo)
                    .setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(titulo);
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("Nome: " + nome).setFont(fontNormal).setFontSize(12));
            document.add(new Paragraph("CPF/CNPJ: " + documento).setFont(fontNormal).setFontSize(12));
            document.add(new Paragraph("Empresa: " + empresa).setFont(fontNormal).setFontSize(12));
            document.add(new Paragraph("\n"));

            // Criação da tabela com 8 colunas
            Table table = new Table(UnitValue.createPercentArray(new float[]{10,10,20,10,15,10,10,15}))
                    .setWidth(UnitValue.createPercentValue(100));

            String[] headers = {"Mês", "Tipo", "Item", "Qtd", "Total Valor", "Método", "Custo Unit", "Lucro"};
            for(String h : headers) {
                table.addHeaderCell(new Cell()
                        .add(new Paragraph(h).setFont(fontTitulo).setFontSize(12))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY));
            }

            double somaBruto = 0;
            for(String[] row : dados) {
                table.addCell(new Cell().add(new Paragraph(row[0]).setFont(fontNormal)).setTextAlignment(TextAlignment.CENTER));
                table.addCell(new Cell().add(new Paragraph(row[1]).setFont(fontNormal)).setTextAlignment(TextAlignment.CENTER));
                table.addCell(new Cell().add(new Paragraph(row[2]).setFont(fontNormal)));
                table.addCell(new Cell().add(new Paragraph(row[3]).setFont(fontNormal)).setTextAlignment(TextAlignment.RIGHT));
                table.addCell(new Cell().add(new Paragraph(row[4]).setFont(fontNormal)).setTextAlignment(TextAlignment.RIGHT));
                table.addCell(new Cell().add(new Paragraph(row[5]).setFont(fontNormal)).setTextAlignment(TextAlignment.CENTER));
                table.addCell(new Cell().add(new Paragraph(row[6]).setFont(fontNormal)).setTextAlignment(TextAlignment.RIGHT));
                table.addCell(new Cell().add(new Paragraph(row[7]).setFont(fontNormal)).setTextAlignment(TextAlignment.RIGHT));

                try {
                    somaBruto += Double.parseDouble(row[4]);
                } catch(Exception e){
                    Log.w(TAG, "Erro ao converter valor para cálculo de soma bruta: " + e.getMessage());
                }
            }

            document.add(table);
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("Valor Bruto: " + String.format("%.2f", somaBruto)).setFont(fontNormal));
            document.add(new Paragraph("Valor Líquido: " + calculateTotalNet(dados)).setFont(fontNormal));
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("Documento gerado por SyncroManage em " +
                    new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()))
                    .setFont(fontNormal)
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.RIGHT));

            document.close();
            Log.i(TAG, "Relatório PDF gerado com sucesso!");
            return arquivoPDF;
        } catch (IOException e) {
            Log.e(TAG, "Erro ao criar arquivo PDF", e);
            Toast.makeText(context, "Erro ao gerar PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Exporta os dados do relatório para um arquivo PDF de forma assíncrona.
     *
     * @param context Contexto da aplicação
     * @param dados Lista de arrays de strings com os dados do relatório
     * @param nome Nome do usuário/empresa
     * @param documento CPF/CNPJ do usuário/empresa
     * @param empresa Nome da empresa
     * @param callback Callback para retornar o resultado
     */
    public static void exportarPDFAsync(Context context, List<String[]> dados, String nome,
                                        String documento, String empresa, RelatorioCallback<File> callback) {
        executor.execute(() -> {
            File arquivo = exportarPDF(context, dados, nome, documento, empresa);
            mainHandler.post(() -> callback.onResult(arquivo, arquivo != null,
                    arquivo != null ? "PDF gerado com sucesso" : "Erro ao gerar PDF"));
        });
    }

    /**
     * Método auxiliar para calcular o total líquido a partir do lucro de cada linha.
     *
     * @param dados Lista de arrays de strings com os dados do relatório
     * @return String formatada com o valor líquido total
     */
    private static String calculateTotalNet(List<String[]> dados) {
        double totalNet = 0;
        for(String[] row : dados) {
            try {
                totalNet += Double.parseDouble(row[7]); // Campo "Lucro"
            } catch(Exception e) {
                Log.w(TAG, "Erro ao converter valor para cálculo de lucro: " + e.getMessage());
            }
        }
        return String.format("%.2f", totalNet);
    }

    /**
     * Método para compartilhar o arquivo gerado.
     *
     * @param context Contexto da aplicação
     * @param arquivo Arquivo a ser compartilhado
     * @param mimeType Tipo MIME do arquivo
     */
    public static void compartilharArquivo(Context context, File arquivo, String mimeType) {
        if (arquivo == null || !arquivo.exists()) {
            Toast.makeText(context, "Arquivo não encontrado!", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            android.net.Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    arquivo
            );

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(android.content.Intent.createChooser(intent, "Compartilhar relatório via"));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao compartilhar arquivo", e);
            Toast.makeText(context, "Erro ao compartilhar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
