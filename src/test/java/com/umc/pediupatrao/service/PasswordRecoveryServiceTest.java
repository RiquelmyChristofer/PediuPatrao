package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.PasswordResetToken;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.PasswordResetTokenRepository;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.mockito.ArgumentCaptor;

class PasswordRecoveryServiceTest {
    @Test
    void enviaLinkEArmazenaSomenteHashDoToken() {
        UsuarioRepository usuarioRepository = org.mockito.Mockito.mock(UsuarioRepository.class);
        PasswordResetTokenRepository tokenRepository =
                org.mockito.Mockito.mock(PasswordResetTokenRepository.class);
        RegistroAuditoriaRepository auditoriaRepository =
                org.mockito.Mockito.mock(RegistroAuditoriaRepository.class);
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        Usuario usuario = new Usuario();
        usuario.setId("usuario-1");
        usuario.setUsername("ana");
        usuario.setEmail("ana@example.com");
        when(usuarioRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));

        PasswordRecoveryService service = new PasswordRecoveryService(
                usuarioRepository, tokenRepository, new BCryptPasswordEncoder(),
                new AuditoriaService(auditoriaRepository),
                mailSender, "http://localhost:8080/", "noreply@example.com", "smtp.example.com");

        service.solicitarRedefinicao(" ana@example.com ");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        Matcher matcher = Pattern.compile("/redefinir-senha\\?token=([A-Za-z0-9_-]+)")
                .matcher(messageCaptor.getValue().getText());
        assertTrue(matcher.find());
        assertNotEquals(matcher.group(1), tokenCaptor.getValue().getTokenHash());
        assertTrue(tokenCaptor.getValue().getExpiresAt().after(new Date()));
        assertTrue(messageCaptor.getValue().getText().contains("http://localhost:8080/redefinir-senha?token="));
    }

    @Test
    void emailDesconhecidoNaoDisparaEnvioNemCriaToken() {
        UsuarioRepository usuarioRepository = org.mockito.Mockito.mock(UsuarioRepository.class);
        PasswordResetTokenRepository tokenRepository =
                org.mockito.Mockito.mock(PasswordResetTokenRepository.class);
        RegistroAuditoriaRepository auditoriaRepository =
                org.mockito.Mockito.mock(RegistroAuditoriaRepository.class);
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        when(usuarioRepository.findByEmailIgnoreCase("naoexiste@example.com")).thenReturn(Optional.empty());
        PasswordRecoveryService service = new PasswordRecoveryService(
                usuarioRepository, tokenRepository, new BCryptPasswordEncoder(),
                new AuditoriaService(auditoriaRepository),
                mailSender, "http://localhost:8080", "noreply@example.com", "smtp.example.com");

        service.solicitarRedefinicao("naoexiste@example.com");

        verify(tokenRepository, org.mockito.Mockito.never()).save(any());
        verify(mailSender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
        assertFalse(service.tokenValido(null));
    }

    @Test
    void redefineSenhaComTokenValidoEGravaAuditoria() {
        UsuarioRepository usuarioRepository = org.mockito.Mockito.mock(UsuarioRepository.class);
        PasswordResetTokenRepository tokenRepository =
                org.mockito.Mockito.mock(PasswordResetTokenRepository.class);
        RegistroAuditoriaRepository auditoriaRepository =
                org.mockito.Mockito.mock(RegistroAuditoriaRepository.class);
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Usuario usuario = new Usuario();
        usuario.setId("usuario-1");
        usuario.setPassword(encoder.encode("SenhaAntiga123"));
        when(tokenRepository.consumeValidToken(any(String.class), any(Date.class)))
                .thenReturn(new PasswordResetToken("hash", "usuario-1", new Date()));
        when(usuarioRepository.findById("usuario-1")).thenReturn(Optional.of(usuario));
        PasswordRecoveryService service = new PasswordRecoveryService(
                usuarioRepository, tokenRepository, encoder,
                new AuditoriaService(auditoriaRepository), mailSender,
                "http://localhost:8080", "noreply@example.com", "smtp.example.com");

        assertTrue(service.redefinirSenha("token-raw", "SenhaNova123"));

        assertTrue(encoder.matches("SenhaNova123", usuario.getPassword()));
        verify(usuarioRepository).save(usuario);
        verify(auditoriaRepository).save(any(RegistroAuditoria.class));
    }
}
