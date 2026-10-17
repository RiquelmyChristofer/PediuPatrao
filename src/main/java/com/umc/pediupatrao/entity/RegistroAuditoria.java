package com.umc.pediupatrao.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "auditoria")
public class RegistroAuditoria {
    @Id
    private String id;
    private String usuario;
    private String perfil;
    private String acao;
    private String entidade;
    private String entidadeId;
    private String detalhes;
    private Instant dataHora;
    private List<CampoAuditado> camposAlterados = new ArrayList<>();

    public RegistroAuditoria() {}
    public RegistroAuditoria(String usuario, String acao, String entidade, String entidadeId, String detalhes) {
        this.usuario = usuario;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhes = detalhes;
        this.dataHora = Instant.now();
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
    public String getAcao() { return acao; }
    public void setAcao(String acao) { this.acao = acao; }
    public String getEntidade() { return entidade; }
    public void setEntidade(String entidade) { this.entidade = entidade; }
    public String getEntidadeId() { return entidadeId; }
    public void setEntidadeId(String entidadeId) { this.entidadeId = entidadeId; }
    public String getDetalhes() { return detalhes; }
    public void setDetalhes(String detalhes) { this.detalhes = detalhes; }
    public Instant getDataHora() { return dataHora; }
    public void setDataHora(Instant dataHora) { this.dataHora = dataHora; }
    public List<CampoAuditado> getCamposAlterados() { return camposAlterados; }
    public void setCamposAlterados(List<CampoAuditado> camposAlterados) {
        this.camposAlterados = camposAlterados == null ? new ArrayList<>() : camposAlterados;
    }
}
