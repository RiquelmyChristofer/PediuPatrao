package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    public Optional<Usuario> buscarPorId(String id) { return usuarioRepository.findById(id); }
    public Optional<Usuario> buscarPorUsername(String username) { return usuarioRepository.findByUsername(username); }

    public Usuario salvarUsuario(Usuario usuario) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new IllegalArgumentException("O nome de usuário é obrigatório.");
        }
        boolean novo = usuario.getId() == null;
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("A senha é obrigatória para criar um usuário.");
        }
        if (usuario.getRole() == null || usuario.getRole().isBlank()) usuario.setRole("ATENDENTE");
        usuario.setRole(normalizarRole(usuario.getRole()));
        usuario.setEmail(normalizarEmail(usuario.getEmail()));
        validarEmailUnico(usuario.getEmail(), null);
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Já existe um usuário com esse nome.");
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        Usuario salvo = usuarioRepository.save(usuario);
        auditoriaService.registrar(novo ? "CRIACAO" : "ATUALIZACAO", "USUARIO", salvo.getId(),
                "Perfil " + salvo.getRole() + " para usuário " + salvo.getUsername());
        return salvo;
    }

    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName().equals(existente.getUsername())) {
            throw new AccessDeniedException("Não é permitido excluir o próprio usuário.");
        }
        if ("admin".equalsIgnoreCase(existente.getUsername())) {
            throw new IllegalArgumentException("O usuário administrador padrão não pode ser removido.");
        }
        usuarioRepository.deleteById(id);
        auditoriaService.registrar("EXCLUSAO", "USUARIO", id, "Usuário " + existente.getUsername() + " excluído");
    }

    public Iterable<Usuario> listarTodos() { return usuarioRepository.findAll(); }

    public void atualizarUsuario(String id, Usuario atualizado) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (atualizado.getUsername() == null || atualizado.getUsername().isBlank()) {
            throw new IllegalArgumentException("O nome de usuário é obrigatório.");
        }
        if (authentication != null && authentication.getName().equals(existente.getUsername())) {
            if (!atualizado.getUsername().equals(existente.getUsername())) {
                throw new AccessDeniedException("Não é permitido renomear o próprio usuário.");
            }
            if (atualizado.getRole() != null
                    && !atualizado.getRole().isBlank()
                    && !normalizarRole(atualizado.getRole()).equals(existente.getRole())) {
                throw new AccessDeniedException("Não é permitido alterar o próprio perfil.");
            }
        }
        if (atualizado.getUsername() != null
                && !atualizado.getUsername().equals(existente.getUsername())
                && usuarioRepository.findByUsername(atualizado.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Já existe um usuário com esse nome.");
        }
        String email = normalizarEmail(atualizado.getEmail());
        validarEmailUnico(email, id);
        existente.setUsername(atualizado.getUsername());
        existente.setEmail(email);
        if (atualizado.getRole() != null && !atualizado.getRole().isBlank()) {
            existente.setRole(normalizarRole(atualizado.getRole()));
        }
        if (atualizado.getPassword() != null && !atualizado.getPassword().isBlank()) {
            existente.setPassword(passwordEncoder.encode(atualizado.getPassword()));
        }
        Usuario salvo = usuarioRepository.save(existente);
        auditoriaService.registrar("ATUALIZACAO", "USUARIO", id,
                "Dados/perfil atualizados para usuário " + salvo.getUsername());
    }

    private String normalizarRole(String role) {
        String normalizada = role.trim().toUpperCase();
        if (normalizada.startsWith("ROLE_")) normalizada = normalizada.substring(5);
        if (normalizada.equals("USER") || normalizada.equals("FUNCIONARIO")) normalizada = "ATENDENTE";
        if (!normalizada.equals("ADMIN") && !normalizada.equals("GERENTE")
                && !normalizada.equals("ATENDENTE")) {
            throw new IllegalArgumentException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        return normalizada;
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String normalizado = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalizado.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        return normalizado;
    }

    private void validarEmailUnico(String email, String usuarioId) {
        if (email == null) return;
        usuarioRepository.findByEmailIgnoreCase(email)
                .filter(usuario -> !java.util.Objects.equals(usuario.getId(), usuarioId))
                .ifPresent(usuario -> {
                    throw new IllegalArgumentException("Já existe um usuário com esse e-mail.");
                });
    }
}
