package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.CampoAuditado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ClienteServiceTest {
    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditaValoresAnterioresENovosDeTelefoneEEndereco() {
        var clienteRepository = org.mockito.Mockito.mock(ClienteRepository.class);
        var auditoriaRepository = org.mockito.Mockito.mock(RegistroAuditoriaRepository.class);
        var auditoriaService = new AuditoriaService(auditoriaRepository);
        var clienteService = new ClienteService(clienteRepository, auditoriaService);

        Cliente anterior = new Cliente();
        anterior.setId("cliente-1");
        anterior.setNome("Ana");
        anterior.setTelefone("1111");
        anterior.setLogradouro("Rua A");
        anterior.setNumero("10");
        when(clienteRepository.findById("cliente-1")).thenReturn(Optional.of(anterior));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "atendente01", "senha", List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));

        Cliente atualizado = new Cliente();
        atualizado.setId("cliente-1");
        atualizado.setNome("Ana");
        atualizado.setTelefone("2222");
        atualizado.setLogradouro("Rua B");
        atualizado.setNumero("10");
        clienteService.salvar(atualizado);

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        RegistroAuditoria registro = captor.getValue();
        assertEquals("atendente01", registro.getUsuario());
        assertEquals("ATENDENTE", registro.getPerfil());
        assertEquals("1111", valor(registro, "telefone", true));
        assertEquals("2222", valor(registro, "telefone", false));
        assertEquals("Rua A", valor(registro, "logradouro", true));
        assertEquals("Rua B", valor(registro, "logradouro", false));
    }

    private String valor(RegistroAuditoria registro, String nomeCampo, boolean anterior) {
        CampoAuditado campo = registro.getCamposAlterados().stream()
                .filter(alteracao -> nomeCampo.equals(alteracao.getCampo()))
                .findFirst().orElseThrow();
        return anterior ? campo.getValorAnterior() : campo.getValorNovo();
    }
}
