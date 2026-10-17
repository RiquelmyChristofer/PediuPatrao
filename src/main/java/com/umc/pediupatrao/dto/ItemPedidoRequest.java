package com.umc.pediupatrao.dto;

public class ItemPedidoRequest {
    private String produtoId;
    private int quantidade;

    public String getProdutoId() { return produtoId; }
    public void setProdutoId(String produtoId) { this.produtoId = produtoId; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
}
