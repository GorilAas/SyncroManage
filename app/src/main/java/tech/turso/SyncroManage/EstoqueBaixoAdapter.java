package tech.turso.SyncroManage;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class EstoqueBaixoAdapter extends RecyclerView.Adapter<EstoqueBaixoAdapter.ViewHolder> {

    private List<HomeDAO.EstoqueBaixo> produtos;
    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public EstoqueBaixoAdapter(List<HomeDAO.EstoqueBaixo> produtos) {
        this.produtos = produtos;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_estoque_baixo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HomeDAO.EstoqueBaixo produto = produtos.get(position);

        holder.txtNomeProduto.setText(produto.nome);
        holder.txtQuantidade.setText("Estoque: " + produto.quantidade);
        holder.txtValorUnitario.setText(currencyFormat.format(produto.valorUnitario));

        // Configura o clique no botão para ir direto para a edição do produto
        holder.btnAdicionarEstoque.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), EstoqueActivity.class);
            intent.putExtra("PRODUTO_ID", produto.idEstoque);
            intent.putExtra("ACAO", "EDITAR");
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return produtos.size();
    }

    public void atualizarDados(List<HomeDAO.EstoqueBaixo> novosProdutos) {
        this.produtos = novosProdutos;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtNomeProduto;
        TextView txtQuantidade;
        TextView txtValorUnitario;
        Button btnAdicionarEstoque;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtNomeProduto = itemView.findViewById(R.id.txtNomeProduto);
            txtQuantidade = itemView.findViewById(R.id.txtQuantidade);
            txtValorUnitario = itemView.findViewById(R.id.txtValorUnitario);
            btnAdicionarEstoque = itemView.findViewById(R.id.btnAdicionarEstoque);
        }
    }
}