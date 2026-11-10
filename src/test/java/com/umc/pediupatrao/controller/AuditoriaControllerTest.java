package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditoriaController.class)
@Import(SecurityConfig.class)
class AuditoriaControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditoriaService auditoriaService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void aceitaFiltrosEPaginacaoParaAdmin() throws Exception {
        when(auditoriaService.listar(eq("CLIENTE"), eq("ana"), eq("ATUALIZACAO"),
                eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 9)), eq(1), eq(25)))
                .thenReturn(new PageImpl<>(List.of(),
                        PageRequest.of(1, 25, Sort.by(Sort.Direction.DESC, "dataHora")), 0));

        mockMvc.perform(get("/api/auditoria")
                        .param("entidade", "CLIENTE")
                        .param("usuario", "ana")
                        .param("acao", "ATUALIZACAO")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-09")
                        .param("page", "1")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(auditoriaService).listar("CLIENTE", "ana", "ATUALIZACAO",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9), 1, 25);
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void permiteConsultaSomenteLeituraAoGerente() throws Exception {
        when(auditoriaService.listar(isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(),
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "dataHora")), 0));

        mockMvc.perform(get("/api/auditoria")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void negaConsultaAoAtendente() throws Exception {
        mockMvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void negaAlteracoesNaAuditoria() throws Exception {
        mockMvc.perform(post("/api/auditoria").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }
}
