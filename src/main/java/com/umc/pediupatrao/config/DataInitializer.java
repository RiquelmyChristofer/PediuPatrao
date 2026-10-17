package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo) {
        return args -> {

            var adminExistente = repo.findByUsername("admin");
            if (adminExistente.isEmpty()) {
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                Usuario user = new Usuario();
                user.setUsername("admin");
                user.setPassword(encoder.encode("teste123"));
                user.setRole("ADMIN");
                repo.save(user);
                System.out.println("Usuário admin criado!");
            } else if (!"ADMIN".equalsIgnoreCase(adminExistente.get().getRole())) {
                Usuario admin = adminExistente.get();
                admin.setRole("ADMIN");
                repo.save(admin);
                System.out.println("Perfil administrativo do usuário admin atualizado.");
            }
            for (Usuario usuario : repo.findAll()) {
                if (!"admin".equalsIgnoreCase(usuario.getUsername())
                        && (usuario.getRole() == null || usuario.getRole().isBlank()
                        || "USER".equalsIgnoreCase(usuario.getRole())
                        || "ROLE_USER".equalsIgnoreCase(usuario.getRole())
                        || "FUNCIONARIO".equalsIgnoreCase(usuario.getRole())
                        || "ROLE_FUNCIONARIO".equalsIgnoreCase(usuario.getRole()))) {
                    usuario.setRole("ATENDENTE");
                    repo.save(usuario);
                }
            }
        };
    }
}