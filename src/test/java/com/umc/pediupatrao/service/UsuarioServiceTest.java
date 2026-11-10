package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.CampoAuditado;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class UsuarioServiceTest {
    @Test
    void registraPerfilNovoSemAdicionarSenhaNaAuditoria() {
        UsuarioRepository usuarios = org.mockito.Mockito.mock(UsuarioRepository.class);
        RegistroAuditoriaRepository auditorias = org.mockito.Mockito.mock(RegistroAuditoriaRepository.class);
        PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        Usuario novo = usuario("ana", "GERENTE");
        novo.setPassword("senha-clara");
        when(usuarios.findByUsername("ana")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha-clara")).thenReturn("hash");
        when(usuarios.save(any(Usuario.class))).thenReturn(novo);

        new UsuarioService(usuarios, passwordEncoder, new AuditoriaService(auditorias)).salvarUsuario(novo);

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditorias).save(captor.capture());
        CampoAuditado perfil = captor.getValue().getCamposAlterados().get(0);
        assertEquals("perfil", perfil.getCampo());
        assertNull(perfil.getValorAnterior());
        assertEquals("GERENTE", perfil.getValorNovo());
        assertFalse(captor.getValue().getCamposAlterados().stream()
                .anyMatch(campo -> "senha".equalsIgnoreCase(campo.getCampo())
                        || "password".equalsIgnoreCase(campo.getCampo())));
    }

    @Test
    void registraPerfilAnteriorENovoNaAtualizacaoSemAuditarSenha() {
        UsuarioRepository usuarios = org.mockito.Mockito.mock(UsuarioRepository.class);
        RegistroAuditoriaRepository auditorias = org.mockito.Mockito.mock(RegistroAuditoriaRepository.class);
        PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        Usuario existente = usuario("ana", "ADMIN");
        existente.setId("usuario-1");
        existente.setPassword("hash-antigo");
        when(usuarios.findById("usuario-1")).thenReturn(Optional.of(existente));
        when(usuarios.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Usuario atualizado = usuario("ana", "GERENTE");

        new UsuarioService(usuarios, passwordEncoder, new AuditoriaService(auditorias))
                .atualizarUsuario("usuario-1", atualizado);

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditorias).save(captor.capture());
        CampoAuditado perfil = captor.getValue().getCamposAlterados().get(0);
        assertEquals("perfil", perfil.getCampo());
        assertEquals("ADMIN", perfil.getValorAnterior());
        assertEquals("GERENTE", perfil.getValorNovo());
        assertEquals("hash-antigo", existente.getPassword());
        assertFalse(captor.getValue().getCamposAlterados().stream()
                .anyMatch(campo -> "senha".equalsIgnoreCase(campo.getCampo())
                        || "password".equalsIgnoreCase(campo.getCampo())));
    }

    private Usuario usuario(String username, String role) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setRole(role);
        return usuario;
    }
}
