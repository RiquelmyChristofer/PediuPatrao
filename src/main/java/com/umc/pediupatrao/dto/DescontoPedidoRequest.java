package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

public class DescontoPedidoRequest {
    private BigDecimal percentual;

    public BigDecimal getPercentual() { return percentual; }
    public void setPercentual(BigDecimal percentual) { this.percentual = percentual; }
}
