package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class UsuarioDetailsServiceTest {
    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void carregaOsNovosPerfisEConvertePerfilLegado() {
        var repository = org.mockito.Mockito.mock(UsuarioRepository.class);
        var service = new UsuarioDetailsService(repository);
        when(repository.findByUsername("gerente")).thenReturn(Optional.of(usuario("GERENTE")));
        when(repository.findByUsername("funcionario")).thenReturn(Optional.of(usuario("FUNCIONARIO")));

        assertTrue(service.loadUserByUsername("gerente").getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_GERENTE")));
        assertTrue(service.loadUserByUsername("funcionario").getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
    }

    @Test
    void adminNaoPodeAlterarProprioPerfilParaGerente() {
        var repository = org.mockito.Mockito.mock(UsuarioRepository.class);
        var auditoriaRepository =
                org.mockito.Mockito.mock(com.umc.pediupatrao.repository.RegistroAuditoriaRepository.class);
        Usuario admin = usuario("ADMIN");
        admin.setUsername("admin");
        when(repository.findById("user-1")).thenReturn(Optional.of(admin));
        UsuarioService service = new UsuarioService(repository,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
                new AuditoriaService(auditoriaRepository));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "senha", java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Usuario atualizado = usuario("GERENTE");
        atualizado.setUsername("admin");

        assertThrows(AccessDeniedException.class, () -> service.atualizarUsuario("user-1", atualizado));
    }

    private Usuario usuario(String role) {
        Usuario usuario = new Usuario();
        usuario.setUsername(role.toLowerCase());
        usuario.setPassword("hash");
        usuario.setRole(role);
        return usuario;
    }
}
