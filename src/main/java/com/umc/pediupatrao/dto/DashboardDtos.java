package com.umc.pediupatrao.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Objetos de resposta da API do dashboard administrativo (somente leitura). */
public final class DashboardDtos {
    private DashboardDtos() {}

    public record Periodo(LocalDate inicio, LocalDate fim) {}

    public record Comparativo(BigDecimal faturamentoPeriodoAnterior,
                              BigDecimal variacaoFaturamentoPercentual,
                              long pedidosPeriodoAnterior,
                              BigDecimal variacaoPedidosPercentual) {}

    public record Resumo(Periodo periodo,
                         BigDecimal faturamentoTotal,
                         long totalPedidos,
                         long pedidosValidos,
                         long pedidosCancelados,
                         BigDecimal taxaCancelamentoPercentual,
                         BigDecimal ticketMedio,
                         BigDecimal totalDescontos,
                         long itensVendidos,
                         Comparativo comparativo) {}

    public record PontoVenda(String periodo, long pedidos, BigDecimal faturamento) {}

    public record SerieVendas(Periodo periodo, String agrupamento, List<PontoVenda> pontos) {}

    public record VendaPorHora(int hora, long pedidos, BigDecimal faturamento) {}

    public record VendaPorCategoria(String categoria, long quantidade, BigDecimal faturamento) {}

    public record StatusContagem(String status, long quantidade) {}

    public record SituacaoPedidos(Periodo periodo, long total, List<StatusContagem> status) {}

    public record ProdutoRanking(int posicao, String produtoId, String nome, String categoria,
                                 long quantidade, BigDecimal receita) {}

    public record PedidoRecente(String id, String cliente, String status, BigDecimal valorTotal,
                                Instant dataHoraEntrada, String responsavelRegistro, String itens) {}

    public record Filtros(List<String> status, List<String> categorias, String fusoHorario,
                          int periodoMaximoEmDias, int periodoPadraoEmDias) {}
}
