package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.CampoAuditado;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    private static final Set<String> CAMPOS_ENDERECO = Set.of(
            "cep", "logradouro", "numero", "bairro", "cidade", "estado",
            "complemento", "endereco", "enderecolegado");
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
        registro.setCamposAlterados(camposAlteridosProtegidos(camposAlterados));
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

    public Page<RegistroAuditoria> listar(String entidade, String usuario, String acao,
                                          LocalDate inicio, LocalDate fim, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("A página não pode ser negativa.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("O tamanho da página deve estar entre 1 e 100.");
        }
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        }

        Instant inicioInclusivo = inicio == null ? null : inicio.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant fimExclusivo = fim == null ? null : fim.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return repository.buscar(entidade, usuario, acao, inicioInclusivo, fimExclusivo,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dataHora")));
    }

    public List<RegistroAuditoria> listarTodos() {
        return repository.findAll();
    }

    private List<CampoAuditado> camposAlteridosProtegidos(List<CampoAuditado> campos) {
        if (campos == null || campos.isEmpty()) {
            return List.of();
        }
        return campos.stream()
                .map(campo -> new CampoAuditado(campo.getCampo(),
                        valorProtegido(campo.getCampo(), campo.getValorAnterior()),
                        valorProtegido(campo.getCampo(), campo.getValorNovo())))
                .toList();
    }

    private String valorProtegido(String campo, String valor) {
        if (valor == null || campo == null) {
            return valor;
        }
        String nome = campo.toLowerCase(Locale.ROOT);
        if (nome.equals("telefone") || nome.equals("phone")) {
            return mascararTelefone(valor);
        }
        if (CAMPOS_ENDERECO.contains(nome)) {
            return "alterado";
        }
        return valor;
    }

    private String mascararTelefone(String telefone) {
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() >= 10) {
            return "(" + digitos.substring(0, 2) + ") *****-" + digitos.substring(digitos.length() - 4);
        }
        if (digitos.length() > 4) {
            return "*****-" + digitos.substring(digitos.length() - 4);
        }
        return "*".repeat(Math.max(5, digitos.length()));
    }
}
