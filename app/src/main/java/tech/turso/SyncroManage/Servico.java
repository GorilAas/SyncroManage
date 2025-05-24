package tech.turso.SyncroManage;

import java.io.Serializable;

public class Servico implements Serializable {
    private int id_servico;
    private String id_usuario;
    private String nome;
    private double custo_unitario;
    private double valor_unitario;

    public Servico() {
        // Construtor vazio
    }

    public Servico(int id_servico, String id_usuario, String nome, double custo_unitario, double valor_unitario) {
        this.id_servico = id_servico;
        this.id_usuario = id_usuario;
        this.nome = nome;
        this.custo_unitario = custo_unitario;
        this.valor_unitario = valor_unitario;
    }

    // Getters e Setters (os que você já tinha)
    public int getId_servico() {
        return id_servico;
    }

    public void setId_servico(int id_servico) {
        this.id_servico = id_servico;
    }

    public String getId_usuario() {
        return id_usuario;
    }

    public void setId_usuario(String id_usuario) {
        this.id_usuario = id_usuario;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
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

    /**
     * Retorna o nome do serviço.
     * Este método é usado pelo ArrayAdapter para exibir o nome do serviço
     * de forma legível em componentes como AutoCompleteTextView ou Spinner.
     */
    @Override
    public String toString() {
        return this.nome;
    }
}