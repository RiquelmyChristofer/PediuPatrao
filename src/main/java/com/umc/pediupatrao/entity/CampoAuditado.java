package com.umc.pediupatrao.entity;

public class CampoAuditado {
    private String campo;
    private String valorAnterior;
    private String valorNovo;

    public CampoAuditado() {
    }

    public CampoAuditado(String campo, Object valorAnterior, Object valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior == null ? null : valorAnterior.toString();
        this.valorNovo = valorNovo == null ? null : valorNovo.toString();
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    public void setValorNovo(String valorNovo) {
        this.valorNovo = valorNovo;
    }
}
