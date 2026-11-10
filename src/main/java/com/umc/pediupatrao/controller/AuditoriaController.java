package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {
    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public Page<RegistroAuditoria> listarRegistros(
            @RequestParam(name = "entidade", required = false) String entidade,
            @RequestParam(name = "usuario", required = false) String usuario,
            @RequestParam(name = "acao", required = false) String acao,
            @RequestParam(name = "inicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(name = "fim", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return auditoriaService.listar(entidade, usuario, acao, inicio, fim, page, size);
    }
}
