package tech.turso.SyncroManage;

import java.io.Serializable;

public class Venda implements Serializable {
    private int id_venda;
    private String id_usuario;
    private String tipo_item; // "produto" ou "servico"
    private Integer id_servico_vendido; // null se for produto
    private String nome_item_vendido;
    private double valor_unitario_vendido;
    private int quantidade;
    private double valor_total_venda;
    private String data_hora_venda;
    private String metodo_pagamento;

    // Construtor vazio necessário para serialização
    public Venda() {
    }

    // Construtor completo
    public Venda(int id_venda, String id_usuario, String tipo_item, Integer id_servico_vendido,
                 String nome_item_vendido, double valor_unitario_vendido, int quantidade,
                 double valor_total_venda, String data_hora_venda, String metodo_pagamento) {
        this.id_venda = id_venda;
        this.id_usuario = id_usuario;
        this.tipo_item = tipo_item;
        this.id_servico_vendido = id_servico_vendido;
        this.nome_item_vendido = nome_item_vendido;
        this.valor_unitario_vendido = valor_unitario_vendido;
        this.quantidade = quantidade;
        this.valor_total_venda = valor_total_venda;
        this.data_hora_venda = data_hora_venda;
        this.metodo_pagamento = metodo_pagamento;
    }

    // Construtor sem ID (para novas vendas)
    public Venda(String id_usuario, String tipo_item, Integer id_servico_vendido,
                 String nome_item_vendido, double valor_unitario_vendido, int quantidade,
                 double valor_total_venda, String data_hora_venda, String metodo_pagamento) {
        this.id_usuario = id_usuario;
        this.tipo_item = tipo_item;
        this.id_servico_vendido = id_servico_vendido;
        this.nome_item_vendido = nome_item_vendido;
        this.valor_unitario_vendido = valor_unitario_vendido;
        this.quantidade = quantidade;
        this.valor_total_venda = valor_total_venda;
        this.data_hora_venda = data_hora_venda;
        this.metodo_pagamento = metodo_pagamento;
    }

    // Getters e Setters
    public int getId_venda() {
        return id_venda;
    }

    public void setId_venda(int id_venda) {
        this.id_venda = id_venda;
    }

    public String getId_usuario() {
        return id_usuario;
    }

    public void setId_usuario(String id_usuario) {
        this.id_usuario = id_usuario;
    }

    public String getTipo_item() {
        return tipo_item;
    }

    public void setTipo_item(String tipo_item) {
        this.tipo_item = tipo_item;
    }

    public Integer getId_servico_vendido() {
        return id_servico_vendido;
    }

    public void setId_servico_vendido(Integer id_servico_vendido) {
        this.id_servico_vendido = id_servico_vendido;
    }

    public String getNome_item_vendido() {
        return nome_item_vendido;
    }

    public void setNome_item_vendido(String nome_item_vendido) {
        this.nome_item_vendido = nome_item_vendido;
    }

    public double getValor_unitario_vendido() {
        return valor_unitario_vendido;
    }

    public void setValor_unitario_vendido(double valor_unitario_vendido) {
        this.valor_unitario_vendido = valor_unitario_vendido;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public double getValor_total_venda() {
        return valor_total_venda;
    }

    public void setValor_total_venda(double valor_total_venda) {
        this.valor_total_venda = valor_total_venda;
    }

    public String getData_hora_venda() {
        return data_hora_venda;
    }

    public void setData_hora_venda(String data_hora_venda) {
        this.data_hora_venda = data_hora_venda;
    }

    public String getMetodo_pagamento() {
        return metodo_pagamento;
    }

    public void setMetodo_pagamento(String metodo_pagamento) {
        this.metodo_pagamento = metodo_pagamento;
    }

    // Método para calcular o valor total com base no valor unitário e quantidade
    public void calcularValorTotal() {
        this.valor_total_venda = this.valor_unitario_vendido * this.quantidade;
    }

    @Override
    public String toString() {
        return nome_item_vendido;
    }
}