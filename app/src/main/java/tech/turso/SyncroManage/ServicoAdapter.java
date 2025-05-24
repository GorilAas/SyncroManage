package tech.turso.SyncroManage;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
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

public class ServicoAdapter extends RecyclerView.Adapter<ServicoAdapter.ServicoViewHolder> implements Filterable {
    private static final String TAG = "ServicoAdapter";

    private final List<Servico> servicoList;
    private final List<Servico> servicoListFull;
    private final Context context;
    private OnItemClickListener listener;
    private final NumberFormat currencyFormat;

    public ServicoAdapter(List<Servico> servicoList, Context context) {
        this.servicoList = new ArrayList<>(servicoList);
        this.servicoListFull = new ArrayList<>(servicoList);
        this.context = context;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    }

    @NonNull
    @Override
    public ServicoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_servico, parent, false);
        return new ServicoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServicoViewHolder holder, int position) {
        Servico servico = servicoList.get(position);
        holder.bind(servico);
    }

    @Override
    public int getItemCount() {
        return servicoList.size();
    }

    @Override
    public Filter getFilter() {
        return servicoFilter;
    }

    private final Filter servicoFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Servico> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(servicoListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (Servico servico : servicoListFull) {
                    // Inclui busca pelo nome e descrição para mais flexibilidade
                    if (servico.getNome().toLowerCase().contains(filterPattern)) {
                        filteredList.add(servico);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // Usa DiffUtil para atualizar a lista com animações suaves
            @SuppressWarnings("unchecked")
            List<Servico> filteredList = (List<Servico>) results.values;

            // Calcula as diferenças entre a lista atual e a nova lista filtrada
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                    new ServicoDiffCallback(servicoList, filteredList));

            // Atualiza a lista e notifica as alterações calculadas
            servicoList.clear();
            servicoList.addAll(filteredList);

            // Aplica as atualizações calculadas ao RecyclerView
            diffResult.dispatchUpdatesTo(ServicoAdapter.this);
        }
    };

    /**
     * Atualiza a lista de serviços usando DiffUtil para melhor performance
     *
     * @param newList Nova lista de serviços
     */
    public void updateList(List<Servico> newList) {
        if (newList == null) {
            Log.e(TAG, "Tentativa de atualizar lista com valor nulo");
            return;
        }

        // Calcula as diferenças entre a lista antiga e a nova
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new ServicoDiffCallback(servicoList, newList));

        // Atualiza ambas as listas
        servicoList.clear();
        servicoList.addAll(newList);
        servicoListFull.clear();
        servicoListFull.addAll(newList);

        // Dispara as atualizações calculadas pelo DiffUtil
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Remove um serviço da lista usando DiffUtil para animações suaves
     *
     * @param servico Serviço a ser removido
     */
    public void removeItem(Servico servico) {
        if (servico == null) {
            Log.e(TAG, "Tentativa de remover serviço nulo");
            return;
        }

        int position = servicoList.indexOf(servico);
        if (position == -1) {
            Log.w(TAG, "Serviço não encontrado na lista: " + servico.getNome());
            return;
        }

        // Cria cópias das listas para calcular diferenças
        List<Servico> newList = new ArrayList<>(servicoList);
        newList.remove(servico);

        // Calcula diferenças e atualiza
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new ServicoDiffCallback(servicoList, newList));

        // Atualiza as listas
        servicoList.remove(servico);
        servicoListFull.remove(servico);

        // Aplica atualizações calculadas
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * ViewHolder para itens de serviço
     */
    public class ServicoViewHolder extends RecyclerView.ViewHolder {
        TextView txtNomeServico, txtValorServico, txtCustoServico;
        ImageButton btnMore;

        public ServicoViewHolder(@NonNull View itemView) {
            super(itemView);
            txtNomeServico = itemView.findViewById(R.id.txt_nome_servico);
            txtValorServico = itemView.findViewById(R.id.txt_valor_servico);
            txtCustoServico = itemView.findViewById(R.id.txt_custo_servico);
            btnMore = itemView.findViewById(R.id.btn_more);
        }

        /**
         * Vincula dados do serviço aos elementos de UI
         */
        public void bind(Servico servico) {
            // Evita NullPointerException com validações
            txtNomeServico.setText(servico.getNome() != null ? servico.getNome() : "");

            // Formata valores monetários
            try {
                txtValorServico.setText(currencyFormat.format(servico.getValor_unitario()));
                txtCustoServico.setText(currencyFormat.format(servico.getCusto_unitario()));
            } catch (Exception e) {
                // Em caso de erro na formatação, mostra 0
                Log.e(TAG, "Erro ao formatar valores monetários: " + e.getMessage());
                txtValorServico.setText(currencyFormat.format(0));
                txtCustoServico.setText(currencyFormat.format(0));
            }

            // Configura o menu popup
            btnMore.setOnClickListener(v -> showPopupMenu(v, servico));

            // Configura clique no item
            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onItemClick(servico));
            }
        }
    }

    /**
     * Mostra o menu popup com opções para o serviço
     */
    private void showPopupMenu(View view, Servico servico) {
        if (listener == null) return; // Evita erro se listener não estiver definido

        try {
            PopupMenu popupMenu = new PopupMenu(context, view);
            popupMenu.inflate(R.menu.menu_servico_item);

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_edit) {
                    listener.onEditClick(servico);
                    return true;
                } else if (id == R.id.action_delete) {
                    listener.onDeleteClick(servico);
                    return true;
                }
                return false;
            });

            popupMenu.show();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao mostrar popup menu: " + e.getMessage());
        }
    }

    /**
     * Interface para manipular cliques nos itens
     */
    public interface OnItemClickListener {
        void onItemClick(Servico servico);
        void onEditClick(Servico servico);
        void onDeleteClick(Servico servico);
    }

    /**
     * Define o listener para eventos de clique
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * Classe para calcular diferenças entre listas para o DiffUtil
     */
    private static class ServicoDiffCallback extends DiffUtil.Callback {
        private final List<Servico> oldList;
        private final List<Servico> newList;

        public ServicoDiffCallback(List<Servico> oldList, List<Servico> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Verifica se é o mesmo item por ID
            Servico oldServico = oldList.get(oldItemPosition);
            Servico newServico = newList.get(newItemPosition);
            return oldServico.getId_servico() == newServico.getId_servico();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            // Verifica se o conteúdo é o mesmo
            Servico oldServico = oldList.get(oldItemPosition);
            Servico newServico = newList.get(newItemPosition);

            // Compara todos os campos relevantes para determinar se o conteúdo mudou
            return oldServico.getNome().equals(newServico.getNome()) &&
                    oldServico.getValor_unitario() == newServico.getValor_unitario() &&
                    oldServico.getCusto_unitario() == newServico.getCusto_unitario();
        }
    }
}