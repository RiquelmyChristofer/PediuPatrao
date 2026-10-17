package com.umc.pediupatrao.dto;

import java.util.ArrayList;
import java.util.List;

public class CriarPedidoRequest {
    private String clienteId;
    private List<ItemPedidoRequest> itens = new ArrayList<>();

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public List<ItemPedidoRequest> getItens() { return itens; }
    public void setItens(List<ItemPedidoRequest> itens) { this.itens = itens; }
}
