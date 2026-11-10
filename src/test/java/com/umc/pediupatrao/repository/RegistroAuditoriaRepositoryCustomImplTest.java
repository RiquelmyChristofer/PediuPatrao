package com.umc.pediupatrao.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class RegistroAuditoriaRepositoryCustomImplTest {
    @Test
    void filtraRegistrosEExecutaConsultaPaginadaMaisRecentePrimeiro() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(RegistroAuditoria.class))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(RegistroAuditoria.class))).thenReturn(0L);
        RegistroAuditoriaRepositoryCustomImpl repository =
                new RegistroAuditoriaRepositoryCustomImpl(mongoTemplate);
        Instant inicio = Instant.parse("2026-11-01T00:00:00Z");
        Instant fim = Instant.parse("2026-11-10T00:00:00Z");

        repository.buscar("CLIENTE", "ana", "ATUALIZACAO", inicio, fim,
                PageRequest.of(2, 25, Sort.by(Sort.Direction.DESC, "dataHora")));

        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(consulta.capture(), eq(RegistroAuditoria.class));
        Document filtros = consulta.getValue().getQueryObject();
        assertEquals("CLIENTE", filtros.getString("entidade"));
        assertEquals("ana", filtros.getString("usuario"));
        assertEquals("ATUALIZACAO", filtros.getString("acao"));
        Document intervalo = (Document) filtros.get("dataHora");
        assertEquals(inicio, intervalo.get("$gte"));
        assertEquals(fim, intervalo.get("$lt"));
        assertEquals(25, consulta.getValue().getLimit());
        assertEquals(50, consulta.getValue().getSkip());
        assertEquals(-1, consulta.getValue().getSortObject().getInteger("dataHora"));

        ArgumentCaptor<Query> contagem = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(contagem.capture(), eq(RegistroAuditoria.class));
        assertEquals(0, contagem.getValue().getLimit());
        assertEquals(0, contagem.getValue().getSkip());
    }
}
