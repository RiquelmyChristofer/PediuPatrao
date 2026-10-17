package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.PasswordResetToken;
import com.umc.pediupatrao.repository.PasswordResetTokenRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String mailFrom;
    private final String smtpHost;

    public PasswordRecoveryService(
            UsuarioRepository usuarioRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService,
            JavaMailSender mailSender,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.mail.from:}") String mailFrom,
            @Value("${spring.mail.host:}") String smtpHost) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.mailFrom = mailFrom;
        this.smtpHost = smtpHost;
    }

    public void solicitarRedefinicao(String email) {
        if (email == null || email.isBlank()) return;
        usuarioRepository.findByEmailIgnoreCase(email.trim()).ifPresent(usuario -> {
            if (usuario.getId() == null || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
                log.error("Não foi possível gerar recuperação: usuário sem ID ou e-mail cadastrado.");
                return;
            }
            if (smtpHost.isBlank() || mailFrom.isBlank()) {
                log.error("Recuperação de senha solicitada, mas SMTP_HOST ou MAIL_FROM não está configurado.");
                return;
            }

            tokenRepository.deleteByUserId(usuario.getId());
            String token = criarToken();
            String hash = hashToken(token);
            Date expiresAt = Date.from(Instant.now().plus(TOKEN_LIFETIME));
            tokenRepository.save(new PasswordResetToken(hash, usuario.getId(), expiresAt));

            try {
                SimpleMailMessage mensagem = new SimpleMailMessage();
                mensagem.setFrom(mailFrom);
                mensagem.setTo(usuario.getEmail());
                mensagem.setSubject("Redefinição de senha - Pediu Patrão");
                mensagem.setText("Olá, " + usuario.getUsername() + ".\n\n"
                        + "Use o link a seguir para definir uma nova senha. "
                        + "Ele expira em 30 minutos e só pode ser usado uma vez:\n\n"
                        + baseUrl + "/redefinir-senha?token=" + token + "\n\n"
                        + "Se você não solicitou a redefinição, ignore esta mensagem.");
                mailSender.send(mensagem);
            } catch (MailException ex) {
                tokenRepository.deleteById(hash);
                log.error("Falha ao enviar o e-mail de redefinição de senha.", ex);
            }
        });
    }

    public boolean tokenValido(String token) {
        if (token == null || token.isBlank()) return false;
        return tokenRepository.findByTokenHashAndExpiresAtAfter(hashToken(token), new Date()).isPresent();
    }

    public boolean redefinirSenha(String token, String senha) {
        if (token == null || token.isBlank()) return false;
        if (senha == null || senha.length() < 8) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
        }
        String hash = hashToken(token);
        PasswordResetToken tokenAtivo = tokenRepository.consumeValidToken(hash, new Date());
        if (tokenAtivo == null) return false;

        var usuario = usuarioRepository.findById(tokenAtivo.getUserId());
        if (usuario.isEmpty()) {
            return false;
        }
        usuario.get().setPassword(passwordEncoder.encode(senha));
        usuarioRepository.save(usuario.get());
        auditoriaService.registrar("REDEFINICAO_SENHA", "USUARIO", usuario.get().getId(),
                "Senha redefinida por link de recuperação.");
        return true;
    }

    private String criarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 não está disponível nesta JVM.", ex);
        }
    }
}
