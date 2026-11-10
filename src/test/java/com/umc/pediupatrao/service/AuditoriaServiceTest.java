package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class AuditoriaServiceTest {
    @Test
    void encaminhaFiltrosDatasEOrdenacaoPaginadaAoRepositorio() {
        RegistroAuditoriaRepository repository = org.mockito.Mockito.mock(RegistroAuditoriaRepository.class);
        AuditoriaService service = new AuditoriaService(repository);
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 9);

        service.listar("CLIENTE", "ana", "ATUALIZACAO", inicio, fim, 2, 50);

        ArgumentCaptor<Instant> dataInicial = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> dataFinal = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> paginacao = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscar(org.mockito.ArgumentMatchers.eq("CLIENTE"),
                org.mockito.ArgumentMatchers.eq("ana"), org.mockito.ArgumentMatchers.eq("ATUALIZACAO"),
                dataInicial.capture(), dataFinal.capture(), paginacao.capture());
        assertEquals(inicio.atStartOfDay().toInstant(ZoneOffset.UTC), dataInicial.getValue());
        assertEquals(fim.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), dataFinal.getValue());
        assertEquals(2, paginacao.getValue().getPageNumber());
        assertEquals(50, paginacao.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, paginacao.getValue().getSort().getOrderFor("dataHora").getDirection());
    }

    @Test
    void rejeitaPaginacaoMaiorQueCem() {
        AuditoriaService service = new AuditoriaService(
                org.mockito.Mockito.mock(RegistroAuditoriaRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.listar(null, null, null, null, null, 0, 101));
    }
}
