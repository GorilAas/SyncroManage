package tech.turso.SyncroManage;

import java.io.Serializable;

public class Estoque implements Serializable {
    private int id_estoque;
    private String id_usuario;
    private String nome_produto;
    private double custo_unitario;
    private double valor_unitario;
    private int quantidade;

    // Construtor vazio necessário para serialização
    public Estoque() {
    }

    // Construtor completo
    public Estoque(int id_estoque, String id_usuario, String nome_produto, double custo_unitario, double valor_unitario, int quantidade) {
        this.id_estoque = id_estoque;
        this.id_usuario = id_usuario;
        this.nome_produto = nome_produto;
        this.custo_unitario = custo_unitario;
        this.valor_unitario = valor_unitario;
        this.quantidade = quantidade;
    }

    // Construtor sem ID (útil para novas inserções)
    public Estoque(String id_usuario, String nome_produto, double custo_unitario, double valor_unitario, int quantidade) {
        this.id_usuario = id_usuario;
        this.nome_produto = nome_produto;
        this.custo_unitario = custo_unitario;
        this.valor_unitario = valor_unitario;
        this.quantidade = quantidade;
    }

    // Getters e Setters
    public int getId_estoque() {
        return id_estoque;
    }

    public void setId_estoque(int id_estoque) {
        this.id_estoque = id_estoque;
    }

    public String getId_usuario() {
        return id_usuario;
    }

    public void setId_usuario(String id_usuario) {
        this.id_usuario = id_usuario;
    }

    public String getNome_produto() {
        return nome_produto;
    }

    public void setNome_produto(String nome_produto) {
        this.nome_produto = nome_produto;
    }

    public double getCusto_unitario() {
        return custo_unitario;
    }

    public void setCusto_unitario(double custo_unitario) {
        this.custo_unitario = custo_unitario;
    }

    public double getValor_unitario() {
        return valor_unitario;
    }

    public void setValor_unitario(double valor_unitario) {
        this.valor_unitario = valor_unitario;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    // Método para calcular o valor total do estoque
    public double getValorTotalEstoque() {
        return valor_unitario * quantidade;
    }

    // Método para calcular o custo total do estoque
    public double getCustoTotalEstoque() {
        return custo_unitario * quantidade;
    }

    // Método para calcular o lucro potencial total
    public double getLucroPotencialTotal() {
        return getValorTotalEstoque() - getCustoTotalEstoque();
    }

    @Override
    public String toString() {
        return this.nome_produto; // Exibe o nome do produto na lista
    }
}