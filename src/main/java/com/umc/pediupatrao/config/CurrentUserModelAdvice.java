package com.umc.pediupatrao.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class CurrentUserModelAdvice {
    @ModelAttribute("perfilAdmin")
    public boolean perfilAdmin() {
        return hasRole("ROLE_ADMIN");
    }

    @ModelAttribute("perfilGerente")
    public boolean perfilGerente() {
        return hasRole("ROLE_GERENTE");
    }

    @ModelAttribute("perfilAtendente")
    public boolean perfilAtendente() {
        return hasRole("ROLE_ATENDENTE");
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
