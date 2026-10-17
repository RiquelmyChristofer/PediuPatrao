package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(csrfRequestHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/esqueceu-senha", "/redefinir-senha",
                        "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/usuarios", "/usuarios/**", "/api/usuarios", "/api/usuarios/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/auditoria", "/auditoria/**",
                        "/api/auditoria", "/api/auditoria/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers("/auditoria", "/auditoria/**", "/api/auditoria", "/api/auditoria/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/produtos", "/api/produtos/**").hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                .requestMatchers("/api/produtos", "/api/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/clientes", "/api/clientes/**").hasAnyRole("GERENTE", "ATENDENTE")
                .requestMatchers("/api/clientes", "/api/clientes/**").hasRole("ATENDENTE")
                .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/**").hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                .requestMatchers(HttpMethod.POST, "/api/pedidos/*/desconto",
                        "/api/pedidos/*/cancelamento").hasRole("GERENTE")
                .requestMatchers("/api/pedidos", "/api/pedidos/**").hasAnyRole("GERENTE", "ATENDENTE")
                .requestMatchers(HttpMethod.GET, "/produtos", "/produtos/**").hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                .requestMatchers("/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/clientes/novo", "/clientes/editar/**").hasRole("ATENDENTE")
                .requestMatchers(HttpMethod.GET, "/clientes", "/clientes/**").hasAnyRole("GERENTE", "ATENDENTE")
                .requestMatchers("/clientes/**").hasRole("ATENDENTE")
                .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/**").hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                .requestMatchers(HttpMethod.POST, "/pedidos/*/desconto",
                        "/pedidos/*/cancelamento").hasRole("GERENTE")
                .requestMatchers("/pedidos/**").hasAnyRole("GERENTE", "ATENDENTE")
                .requestMatchers("/admin").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
                .successHandler(authenticationSuccessHandler())
            )
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll());
        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> response.sendRedirect("/");
    }
}
