package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {
    @Id
    private String id;
    private String clienteId;
    private String tipoPizza;
    private String status;
    private int quantidade;
    private String sabor;
    private List<ItemPedido> itens = new ArrayList<>();
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal descontoPercentual = BigDecimal.ZERO;
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    private BigDecimal valorTotal = BigDecimal.ZERO;
    private String responsavelRegistro;
    private Instant dataHoraEntrada;
    private Instant dataHoraSaida;
    private String responsavelSaida;
    private String justificativaCancelamento;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getTipoPizza() {
        return tipoPizza;
    }

    public void setTipoPizza(String tipoPizza) {
        this.tipoPizza = tipoPizza;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public String getSabor() {
        return sabor;
    }

    public void setSabor(String sabor) {
        this.sabor = sabor;
    }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens == null ? new ArrayList<>() : itens; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(BigDecimal descontoPercentual) { this.descontoPercentual = descontoPercentual; }
    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }
    public String getResponsavelRegistro() { return responsavelRegistro; }
    public void setResponsavelRegistro(String responsavelRegistro) { this.responsavelRegistro = responsavelRegistro; }
    public Instant getDataHoraEntrada() { return dataHoraEntrada; }
    public void setDataHoraEntrada(Instant dataHoraEntrada) { this.dataHoraEntrada = dataHoraEntrada; }
    public Instant getDataHoraSaida() { return dataHoraSaida; }
    public void setDataHoraSaida(Instant dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }
    public String getResponsavelSaida() { return responsavelSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }
    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) {
        this.justificativaCancelamento = justificativaCancelamento;
    }
    
    
}
