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

public class ItensAltaAdapter extends RecyclerView.Adapter<ItensAltaAdapter.ViewHolder> {

    private List<HomeDAO.ItemEmAlta> itens;
    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public ItensAltaAdapter(List<HomeDAO.ItemEmAlta> itens) {
        this.itens = itens;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_produto_alta, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HomeDAO.ItemEmAlta item = itens.get(position);

        holder.txtNomeProduto.setText(item.nome);
        holder.txtTipoProduto.setText(item.tipo);
        holder.txtQuantidade.setText("Qtd: " + item.quantidade);
        holder.txtValorTotal.setText(currencyFormat.format(item.valorTotal));
    }

    @Override
    public int getItemCount() {
        return itens.size();
    }

    public void atualizarDados(List<HomeDAO.ItemEmAlta> novosItens) {
        this.itens = novosItens;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtNomeProduto;
        TextView txtTipoProduto;
        TextView txtQuantidade;
        TextView txtValorTotal;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtNomeProduto = itemView.findViewById(R.id.txtNomeProduto);
            txtTipoProduto = itemView.findViewById(R.id.txtTipoProduto);
            txtQuantidade = itemView.findViewById(R.id.txtQuantidade);
            txtValorTotal = itemView.findViewById(R.id.txtValorTotal);
        }
    }
}