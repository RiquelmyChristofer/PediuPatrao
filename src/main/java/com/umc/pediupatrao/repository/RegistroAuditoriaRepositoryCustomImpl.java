package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class RegistroAuditoriaRepositoryCustomImpl implements RegistroAuditoriaRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    public RegistroAuditoriaRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<RegistroAuditoria> buscar(String entidade, String usuario, String acao,
                                          Instant inicio, Instant fim, Pageable pageable) {
        List<RegistroAuditoria> registros = mongoTemplate.find(
                criarConsulta(entidade, usuario, acao, inicio, fim).with(pageable), RegistroAuditoria.class);
        long total = mongoTemplate.count(criarConsulta(entidade, usuario, acao, inicio, fim),
                RegistroAuditoria.class);
        return new PageImpl<>(registros, pageable, total);
    }

    private Query criarConsulta(String entidade, String usuario, String acao, Instant inicio, Instant fim) {
        Query consulta = new Query();
        adicionarFiltro(consulta, "entidade", entidade);
        adicionarFiltro(consulta, "usuario", usuario);
        adicionarFiltro(consulta, "acao", acao);
        if (inicio != null || fim != null) {
            Criteria dataHora = Criteria.where("dataHora");
            if (inicio != null) {
                dataHora.gte(inicio);
            }
            if (fim != null) {
                dataHora.lt(fim);
            }
            consulta.addCriteria(dataHora);
        }
        return consulta;
    }

    private void adicionarFiltro(Query consulta, String campo, String valor) {
        if (valor != null && !valor.isBlank()) {
            consulta.addCriteria(Criteria.where(campo).is(valor.trim()));
        }
    }
}
