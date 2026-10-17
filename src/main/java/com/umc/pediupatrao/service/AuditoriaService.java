package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.CampoAuditado;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    private final RegistroAuditoriaRepository repository;

    public AuditoriaService(RegistroAuditoriaRepository repository) {
        this.repository = repository;
    }

    public void registrar(String acao, String entidade, String entidadeId, String detalhes) {
        registrar(acao, entidade, entidadeId, detalhes, List.of());
    }

    public void registrar(String acao, String entidade, String entidadeId, String detalhes,
                          List<CampoAuditado> camposAlterados) {
        RegistroAuditoria registro = new RegistroAuditoria(usuarioAtual(), acao, entidade, entidadeId, detalhes);
        registro.setPerfil(perfilAtual());
        registro.setCamposAlterados(camposAlterados);
        repository.save(registro);
    }

    public String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                ? auth.getName() : "sistema";
    }

    public String perfilAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth instanceof AnonymousAuthenticationToken ? "SISTEMA"
                : auth.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring(5))
                    .findFirst().orElse("DESCONHECIDO");
    }

    public void exigirGerente() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean gerente = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_GERENTE".equals(authority.getAuthority()));
        if (!gerente) {
            throw new AccessDeniedException("Esta operação é exclusiva do perfil GERENTE.");
        }
    }

    public List<RegistroAuditoria> listarTodos() {
        return repository.findAll();
    }
}
