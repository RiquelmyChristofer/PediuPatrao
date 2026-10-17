package com.umc.pediupatrao.service;

import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {
    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
            .map(usuario -> {
                String role = usuario.getRole();
                if (role == null || role.isBlank()) role = "ATENDENTE";
                role = role.trim().toUpperCase();
                if (role.startsWith("ROLE_")) role = role.substring(5);
                if (role.equals("USER") || role.equals("FUNCIONARIO")) role = "ATENDENTE";
                if (!role.equals("ADMIN") && !role.equals("GERENTE") && !role.equals("ATENDENTE")) {
                    role = "ATENDENTE";
                }
                return User.builder()
                    .username(usuario.getUsername())
                    .password(usuario.getPassword())
                    .roles(role)
                    .build();
            })
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
