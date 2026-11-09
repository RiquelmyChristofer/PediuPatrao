package com.umc.pediupatrao.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prova que a autorização é aplicada no backend (Spring Security), e não apenas ocultando botões:
 * as requisições abaixo chamam as APIs diretamente com cada perfil.
 */
@WebMvcTest(controllers = {DashboardController.class, PedidoController.class,
        AuditoriaController.class, UsuarioController.class})
@Import(SecurityConfig.class)
class SecurityAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    // ------------------------------------------------------------ dashboard
    @Test
    void dashboardExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")).andExpect(status().is3xxRedirection());
    }

    @Test
    void dashboardNegadoParaAtendente() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/filtros").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardPermitidoParaGerenteEAdmin() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/resumo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void dashboardRejeitaDataInvalidaComStatus400() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo").param("inicio", "31-12-2026")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------- regras da Atividade 1
    @Test
    void descontoECancelamentoSomenteGerente() throws Exception {
        String corpoDesconto = "{\"percentual\":10}";
        String corpoCancelamento = "{\"justificativa\":\"cliente desistiu\"}";
        for (String papel : new String[] {"ATENDENTE", "ADMIN"}) {
            mockMvc.perform(post("/api/pedidos/abc/desconto").with(user("u").roles(papel)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(corpoDesconto))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/pedidos/abc/cancelamento").with(user("u").roles(papel)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(corpoCancelamento))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/pedidos/abc/desconto").with(user("g").roles("GERENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoDesconto))
                .andExpect(status().isOk());
    }

    @Test
    void postSemCsrfEhBloqueado() throws Exception {
        mockMvc.perform(post("/api/pedidos/abc/desconto").with(user("g").roles("GERENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditoriaSomenteLeituraParaAdminEGerente() throws Exception {
        mockMvc.perform(get("/api/auditoria").with(user("a").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auditoria").with(user("g").roles("GERENTE"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/auditoria").with(user("adm").roles("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/auditoria/1").with(user("adm").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestaoDeUsuariosSomenteAdmin() throws Exception {
        mockMvc.perform(get("/api/usuarios").with(user("g").roles("GERENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios").with(user("a").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios").with(user("adm").roles("ADMIN"))).andExpect(status().isOk());
    }
}
