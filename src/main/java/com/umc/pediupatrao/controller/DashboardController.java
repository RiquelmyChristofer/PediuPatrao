package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardDtos.Filtros;
import com.umc.pediupatrao.dto.DashboardDtos.PedidoRecente;
import com.umc.pediupatrao.dto.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDtos.Resumo;
import com.umc.pediupatrao.dto.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dto.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPorCategoria;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPorHora;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API somente leitura do dashboard administrativo (ADMIN e GERENTE). */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public Resumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.resumo(inicio, fim, status, categoria);
    }

    @GetMapping("/vendas")
    public SerieVendas vendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String agrupamento) {
        return dashboardService.vendasPorPeriodo(inicio, fim, status, categoria, agrupamento);
    }

    @GetMapping("/vendas-por-hora")
    public List<VendaPorHora> vendasPorHora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.vendasPorHora(inicio, fim, status, categoria);
    }

    @GetMapping("/vendas-por-categoria")
    public List<VendaPorCategoria> vendasPorCategoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.vendasPorCategoria(inicio, fim, status, categoria);
    }

    @GetMapping("/pedidos-por-status")
    public SituacaoPedidos pedidosPorStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.situacaoPedidos(inicio, fim, status, categoria);
    }

    @GetMapping("/pedidos-recentes")
    public List<PedidoRecente> pedidosRecentes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(inicio, fim, status, categoria, limite);
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoRanking> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) String ordenarPor) {
        return dashboardService.produtosMaisVendidos(inicio, fim, status, categoria, limite, ordenarPor);
    }

    @GetMapping("/filtros")
    public Filtros filtros() {
        return dashboardService.filtros();
    }
}
