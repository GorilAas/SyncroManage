package tech.turso.SyncroManage;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class VendaAdapter extends RecyclerView.Adapter<VendaAdapter.VendaViewHolder> implements Filterable {
    private final Context context;
    private List<Venda> vendaList;
    private List<Venda> vendaListFull;
    private OnVendaClickListener onVendaClickListener;

    // Cache formatadores para reutilização (não precisam ser recreados a cada bind)
    private final NumberFormat formatoMoeda;
    private final SimpleDateFormat formatoDataEntrada;
    private final SimpleDateFormat formatoDataSaida;

    // Executor para cálculos de filtro em segundo plano
    private final Executor backgroundExecutor;

    public VendaAdapter(Context context, List<Venda> vendaList) {
        this.context = context;
        this.vendaList = new ArrayList<>(vendaList);
        this.vendaListFull = new ArrayList<>(vendaList);

        // Inicializa formatadores uma única vez para melhor desempenho
        this.formatoMoeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        this.formatoDataEntrada = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        this.formatoDataSaida = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        // Executor de thread única para filtros
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
    }

    public interface OnVendaClickListener {
        void onVendaClick(Venda venda, View view);
        void onVendaEdit(Venda venda);
        void onVendaDelete(int id);
    }

    public void setOnVendaClickListener(OnVendaClickListener onVendaClickListener) {
        this.onVendaClickListener = onVendaClickListener;
    }

    @NonNull
    @Override
    public VendaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflar a view uma única vez para cada holder
        View view = LayoutInflater.from(context).inflate(R.layout.item_venda, parent, false);
        return new VendaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VendaViewHolder holder, int position) {
        Venda venda = vendaList.get(position);
        holder.bind(venda);
    }

    @Override
    public int getItemCount() {
        return vendaList.size();
    }

    /**
     * Atualiza a lista de vendas usando DiffUtil para atualizações eficientes na interface
     */
    public void updateList(List<Venda> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }

        // Usa DiffUtil para calcular as diferenças e aplicar apenas as mudanças necessárias
        final List<Venda> oldList = new ArrayList<>(vendaList);
        final List<Venda> updatedList = new ArrayList<>(newList);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return updatedList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldList.get(oldItemPosition).getId_venda() ==
                        updatedList.get(newItemPosition).getId_venda();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Venda oldVenda = oldList.get(oldItemPosition);
                Venda newVenda = updatedList.get(newItemPosition);

                // Verifica se os conteúdos visíveis são os mesmos
                return oldVenda.getId_venda() == newVenda.getId_venda() &&
                        oldVenda.getNome_item_vendido().equals(newVenda.getNome_item_vendido()) &&
                        oldVenda.getValor_total_venda() == newVenda.getValor_total_venda() &&
                        oldVenda.getQuantidade() == newVenda.getQuantidade() &&
                        oldVenda.getData_hora_venda().equals(newVenda.getData_hora_venda()) &&
                        oldVenda.getMetodo_pagamento().equals(newVenda.getMetodo_pagamento()) &&
                        oldVenda.getTipo_item().equals(newVenda.getTipo_item());
            }
        });

        // Atualiza as listas em memória
        vendaList = updatedList;
        vendaListFull = new ArrayList<>(updatedList);

        // Aplica as atualizações calculadas
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public Filter getFilter() {
        return vendaFilter;
    }

    private final Filter vendaFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Venda> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(vendaListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (Venda venda : vendaListFull) {
                    // Melhoria de desempenho: verifica primeiro o null antes de chamar toLowerCase()
                    String nomeItem = venda.getNome_item_vendido();
                    String metodoPagamento = venda.getMetodo_pagamento();
                    String dataHora = venda.getData_hora_venda();

                    if ((nomeItem != null && nomeItem.toLowerCase().contains(filterPattern))
                            || (metodoPagamento != null && metodoPagamento.toLowerCase().contains(filterPattern))
                            || (dataHora != null && dataHora.contains(filterPattern))) {
                        filteredList.add(venda);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            List<Venda> filteredList = (List<Venda>) results.values;
            updateList(filteredList);
        }
    };

    class VendaViewHolder extends RecyclerView.ViewHolder {
        TextView nomeItem, valorTotal, quantidade, dataVenda, metodoPagamento;
        ImageView iconeVenda, btnOpcoes;

        public VendaViewHolder(@NonNull View itemView) {
            super(itemView);
            // Encontra todas as views uma única vez na criação do holder
            nomeItem = itemView.findViewById(R.id.tv_nome_item_venda);
            valorTotal = itemView.findViewById(R.id.tv_valor_total_venda);
            quantidade = itemView.findViewById(R.id.tv_quantidade_venda);
            dataVenda = itemView.findViewById(R.id.tv_data_venda);
            metodoPagamento = itemView.findViewById(R.id.tv_metodo_pagamento);
            iconeVenda = itemView.findViewById(R.id.img_icone_venda);
            btnOpcoes = itemView.findViewById(R.id.btn_opcoes_venda);
        }

        /**
         * Método dedicado para realizar o binding dos dados com as views
         */
        public void bind(final Venda venda) {
            // Formatando a data com fallback seguro
            String dataFormatada = venda.getData_hora_venda();
            try {
                Date dataVenda = formatoDataEntrada.parse(venda.getData_hora_venda());
                if (dataVenda != null) {
                    dataFormatada = formatoDataSaida.format(dataVenda);
                }
            } catch (Exception e) {
                // Mantém o formato original em caso de erro
            }

            // Define o ícone com base no tipo de venda - verificação segura contra null
            String tipoItem = venda.getTipo_item();
            int iconeResource = (tipoItem != null && tipoItem.equalsIgnoreCase("produto")) ?
                    R.drawable.ic_estoque : R.drawable.ic_servico;
            iconeVenda.setImageResource(iconeResource);

            // Configura os textos com verificações de null
            dataVenda.setText(dataFormatada != null ? dataFormatada : "");
            nomeItem.setText(venda.getNome_item_vendido() != null ? venda.getNome_item_vendido() : "");
            valorTotal.setText(formatoMoeda.format(venda.getValor_total_venda()));
            quantidade.setText(String.format(Locale.getDefault(), "Qtd: %d", venda.getQuantidade()));
            metodoPagamento.setText(venda.getMetodo_pagamento() != null ? venda.getMetodo_pagamento() : "");

            // Configura eventos com checagem nula para o listener
            itemView.setOnClickListener(v -> {
                if (onVendaClickListener != null) {
                    onVendaClickListener.onVendaClick(venda, v);
                }
            });

            btnOpcoes.setOnClickListener(v -> {
                if (onVendaClickListener == null) return;

                // Cria menu de contexto com opções
                PopupMenu popupMenu = new PopupMenu(context, v);
                popupMenu.inflate(R.menu.menu_venda_item);
                popupMenu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.menu_venda_editar) {
                        onVendaClickListener.onVendaEdit(venda);
                        return true;
                    } else if (itemId == R.id.menu_venda_excluir) {
                        onVendaClickListener.onVendaDelete(venda.getId_venda());
                        return true;
                    }
                    return false;
                });
                popupMenu.show();
            });
        }
    }
}