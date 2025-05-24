package tech.turso.SyncroManage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador para exibir dados do relatório na RecyclerView com otimizações
 */
public class RelatorioAdapter extends RecyclerView.Adapter<RelatorioAdapter.RelatorioViewHolder> {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private List<String[]> dados;

    public RelatorioAdapter(List<String[]> dados) {
        this.dados = dados;
    }

    public void atualizarDados(List<String[]> novosDados) {
        this.dados = novosDados;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RelatorioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_relatorio, parent, false);
        return new RelatorioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RelatorioViewHolder holder, int position) {
        String[] item = dados.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return dados != null ? dados.size() : 0;
    }

    /**
     * ViewHolder para itens do relatório com método de binding otimizado
     */
    static class RelatorioViewHolder extends RecyclerView.ViewHolder {
        TextView tvMes, tvTipo, tvItem, tvQuantidade, tvValor, tvMetodo, tvCusto, tvLucro;

        public RelatorioViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMes = itemView.findViewById(R.id.tv_mes);
            tvTipo = itemView.findViewById(R.id.tv_tipo);
            tvItem = itemView.findViewById(R.id.tv_item);
            tvQuantidade = itemView.findViewById(R.id.tv_quantidade);
            tvValor = itemView.findViewById(R.id.tv_valor);
            tvMetodo = itemView.findViewById(R.id.tv_metodo);
            tvCusto = itemView.findViewById(R.id.tv_custo);
            tvLucro = itemView.findViewById(R.id.tv_lucro);
        }

        /**
         * Método para vincular dados ao ViewHolder com otimizações
         */
        public void bind(String[] item) {
            // Configuração básica
            tvMes.setText(item[0]);          // Mês
            tvTipo.setText(item[1]);         // Tipo
            tvItem.setText(item[2]);         // Item
            tvQuantidade.setText(item[3]);   // Quantidade
            tvMetodo.setText(item[5]);       // Método de pagamento

            // Formatação otimizada de valores monetários
            tvValor.setText(formatarValorMonetario(item[4]));
            tvCusto.setText(formatarValorMonetario(item[6]));
            tvLucro.setText(formatarValorMonetario(item[7]));
        }

        /**
         * Formata valor monetário usando o formatador estático
         */
        private String formatarValorMonetario(String valor) {
            try {
                double valorDouble = Double.parseDouble(valor);
                return CURRENCY_FORMAT.format(valorDouble);
            } catch (NumberFormatException e) {
                return "R$ " + valor;
            }
        }
    }
}