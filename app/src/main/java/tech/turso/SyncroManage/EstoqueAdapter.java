package tech.turso.SyncroManage;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EstoqueAdapter extends RecyclerView.Adapter<EstoqueAdapter.EstoqueViewHolder> implements Filterable {

    private List<Estoque> listaEstoque;
    private List<Estoque> listaEstoqueCompleta;
    private final Context context;
    private OnItemClickListener listener;
    private final NumberFormat formatoMoeda;

    // ViewHolder Pattern
    static class EstoqueViewHolder extends RecyclerView.ViewHolder {
        TextView textNomeProduto;
        TextView textValorUnitario;
        TextView textQuantidade;
        TextView textValorTotal;
        ImageButton menuItemOpcoes;

        EstoqueViewHolder(View itemView) {
            super(itemView);
            textNomeProduto = itemView.findViewById(R.id.textNomeProduto);
            textValorUnitario = itemView.findViewById(R.id.textValorUnitario);
            textQuantidade = itemView.findViewById(R.id.textQuantidade);
            textValorTotal = itemView.findViewById(R.id.textValorTotal);
            menuItemOpcoes = itemView.findViewById(R.id.menuItemOpcoes);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(Estoque item);
        void onEditClick(Estoque item);
        void onAjustQuantidadeClick(Estoque item);
        void onDeleteClick(Estoque item);
    }

    public EstoqueAdapter(Context context, List<Estoque> listaEstoque, OnItemClickListener listener) {
        this.context = context;
        this.listaEstoque = new ArrayList<>(listaEstoque);
        this.listaEstoqueCompleta = new ArrayList<>(listaEstoque);
        this.listener = listener;
        this.formatoMoeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    }

    @NonNull
    @Override
    public EstoqueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_estoque, parent, false);
        return new EstoqueViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull EstoqueViewHolder holder, int position) {
        final Estoque item = listaEstoque.get(position);

        // Configurando dados
        holder.textNomeProduto.setText(item.getNome_produto());
        holder.textValorUnitario.setText(formatoMoeda.format(item.getValor_unitario()));
        holder.textQuantidade.setText(String.valueOf(item.getQuantidade()));
        holder.textValorTotal.setText(formatoMoeda.format(item.getValorTotalEstoque()));

        // Configurando listener de clique
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });

        // Configurando menu de opções
        holder.menuItemOpcoes.setOnClickListener(v -> showPopupMenu(holder.menuItemOpcoes, item));
    }

    @Override
    public int getItemCount() {
        return listaEstoque.size();
    }

    /**
     * Atualiza a lista de itens utilizando DiffUtil para melhor performance
     * @param novaLista Nova lista de itens
     */
    public void atualizarDados(List<Estoque> novaLista) {
        // Usa DiffUtil para calcular mudanças entre as listas
        EstoqueDiffCallback diffCallback = new EstoqueDiffCallback(listaEstoque, novaLista);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        // Atualiza as listas
        listaEstoque.clear();
        listaEstoque.addAll(novaLista);
        listaEstoqueCompleta = new ArrayList<>(novaLista);

        // Notifica as mudanças de forma eficiente
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Remove um item específico da lista
     * @param item Item a ser removido
     */
    public void removeItem(Estoque item) {
        int position = listaEstoque.indexOf(item);
        if (position != -1) {
            listaEstoque.remove(position);
            notifyItemRemoved(position);

            // Remove também da lista completa
            listaEstoqueCompleta.remove(item);
        }
    }

    @Override
    public Filter getFilter() {
        return estoqueFilter;
    }

    private final Filter estoqueFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Estoque> listaFiltrada = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                listaFiltrada.addAll(listaEstoqueCompleta);
            } else {
                String filtroPattern = constraint.toString().toLowerCase().trim();

                for (Estoque item : listaEstoqueCompleta) {
                    // Realiza pesquisa no nome do produto
                    if (item.getNome_produto().toLowerCase().contains(filtroPattern)) {
                        listaFiltrada.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = listaFiltrada;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            @SuppressWarnings("unchecked")
            List<Estoque> listaFiltrada = (List<Estoque>) results.values;

            // Usa DiffUtil para calcular mudanças e atualizar eficientemente
            EstoqueDiffCallback diffCallback = new EstoqueDiffCallback(listaEstoque, listaFiltrada);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

            listaEstoque.clear();
            listaEstoque.addAll(listaFiltrada);

            diffResult.dispatchUpdatesTo(EstoqueAdapter.this);
        }
    };

    private void showPopupMenu(View view, Estoque item) {
        if (listener == null) return;

        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.inflate(R.menu.menu_estoque_item);

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.menu_editar) {
                listener.onEditClick(item);
                return true;
            } else if (id == R.id.menu_ajustar_estoque) {
                listener.onAjustQuantidadeClick(item);
                return true;
            } else if (id == R.id.menu_excluir) {
                listener.onDeleteClick(item);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    /**
     * Classe auxiliar para calcular as diferenças entre duas listas com DiffUtil
     */
    private static class EstoqueDiffCallback extends DiffUtil.Callback {
        private final List<Estoque> listaAntiga;
        private final List<Estoque> listaNova;

        EstoqueDiffCallback(List<Estoque> listaAntiga, List<Estoque> listaNova) {
            this.listaAntiga = listaAntiga;
            this.listaNova = listaNova;
        }

        @Override
        public int getOldListSize() {
            return listaAntiga.size();
        }

        @Override
        public int getNewListSize() {
            return listaNova.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Compara IDs para verificar se os itens são os mesmos
            return listaAntiga.get(oldItemPosition).getId_estoque() ==
                    listaNova.get(newItemPosition).getId_estoque();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Estoque itemAntigo = listaAntiga.get(oldItemPosition);
            Estoque itemNovo = listaNova.get(newItemPosition);

            // Compara o conteúdo dos itens
            return itemAntigo.getNome_produto().equals(itemNovo.getNome_produto()) &&
                    itemAntigo.getValor_unitario() == itemNovo.getValor_unitario() &&
                    itemAntigo.getQuantidade() == itemNovo.getQuantidade();
        }
    }
}