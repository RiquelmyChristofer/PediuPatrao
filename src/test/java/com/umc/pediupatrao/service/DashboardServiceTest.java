package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDtos.Resumo;
import com.umc.pediupatrao.dto.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dto.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPorCategoria;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPorHora;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 7);

    private PedidoRepository pedidoRepository;
    private ProdutoRepository produtoRepository;
    private ClienteRepository clienteRepository;
    private DashboardService service;

    @BeforeEach
    void configurar() {
        pedidoRepository = mock(PedidoRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        Clock relogio = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZONA);
        service = new DashboardService(pedidoRepository, produtoRepository, clienteRepository, relogio);
        Produto pizza = produto("p1", "Pizza Calabresa", "Pizza");
        Produto refri = produto("p2", "Refrigerante", "Bebida");
        when(produtoRepository.findAllById(anyIterable())).thenReturn(List.of(pizza, refri));
    }

    @Test
    void resumoConsideraApenasVendasValidasParaFaturamento() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(
                        pedido("1", "FINALIZADO", "50.00", dia(3, 12), item("p1", "Pizza Calabresa", 1, "50.00")),
                        pedido("2", "RECEBIDO", "30.00", dia(4, 19), item("p2", "Refrigerante", 2, "15.00")),
                        pedido("3", "CANCELADO", "100.00", dia(5, 20), item("p1", "Pizza Calabresa", 2, "50.00"))))
                .thenReturn(List.of());

        Resumo resumo = service.resumo(INICIO, FIM, null, null);

        assertEquals(new BigDecimal("80.00"), resumo.faturamentoTotal());
        assertEquals(3, resumo.totalPedidos());
        assertEquals(2, resumo.pedidosValidos());
        assertEquals(1, resumo.pedidosCancelados());
        assertEquals(new BigDecimal("40.00"), resumo.ticketMedio());
        assertEquals(new BigDecimal("33.33"), resumo.taxaCancelamentoPercentual());
        assertEquals(3, resumo.itensVendidos());
        assertNull(resumo.comparativo().variacaoFaturamentoPercentual());
    }

    @Test
    void resumoCalculaVariacaoEmRelacaoAoPeriodoAnterior() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(pedido("1", "FINALIZADO", "80.00", dia(3, 12))))
                .thenReturn(List.of(pedido("0", "FINALIZADO", "40.00", LocalDate.of(2026, 10, 28), 12)));

        Resumo resumo = service.resumo(INICIO, FIM, null, null);

        assertEquals(new BigDecimal("40.00"), resumo.comparativo().faturamentoPeriodoAnterior());
        assertEquals(new BigDecimal("100.00"), resumo.comparativo().variacaoFaturamentoPercentual());
        assertEquals(new BigDecimal("0.00"), resumo.comparativo().variacaoPedidosPercentual());
    }

    @Test
    void resumoSemPedidosRetornaZerosSemErro() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class))).thenReturn(List.of());

        Resumo resumo = service.resumo(INICIO, FIM, null, null);

        assertEquals(new BigDecimal("0.00"), resumo.faturamentoTotal());
        assertEquals(new BigDecimal("0.00"), resumo.ticketMedio());
        assertEquals(0, resumo.totalPedidos());
    }

    @Test
    void periodoPadraoSaoOsUltimos30DiasIncluindoHoje() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class))).thenReturn(List.of());

        Resumo resumo = service.resumo(null, null, null, null);

        assertEquals(LocalDate.of(2026, 11, 9), resumo.periodo().fim());
        assertEquals(LocalDate.of(2026, 10, 11), resumo.periodo().inicio());
    }

    @Test
    void vendasPorDiaPreencheDiasSemMovimento() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(pedido("1", "FINALIZADO", "50.00", dia(2, 12)),
                        pedido("2", "FINALIZADO", "25.00", dia(2, 20))));

        SerieVendas serie = service.vendasPorPeriodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3),
                null, null, "dia");

        assertEquals(3, serie.pontos().size());
        assertEquals(0, serie.pontos().get(0).pedidos());
        assertEquals("2026-11-02", serie.pontos().get(1).periodo());
        assertEquals(2, serie.pontos().get(1).pedidos());
        assertEquals(new BigDecimal("75.00"), serie.pontos().get(1).faturamento());
        assertEquals(new BigDecimal("0.00"), serie.pontos().get(2).faturamento());
    }

    @Test
    void vendasPorSemanaAgrupamNaSegundaFeira() {
        // 2026-11-02 é segunda-feira
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(pedido("1", "FINALIZADO", "10.00", dia(3, 12)),
                        pedido("2", "FINALIZADO", "20.00", dia(8, 12))));

        SerieVendas serie = service.vendasPorPeriodo(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8),
                null, null, "semana");

        assertEquals(1, serie.pontos().size());
        assertEquals("2026-11-02", serie.pontos().get(0).periodo());
        assertEquals(2, serie.pontos().get(0).pedidos());
    }

    @Test
    void vendasPorHoraUsaFusoDeSaoPaulo() {
        // 23:30 em São Paulo = 02:30Z do dia seguinte: deve cair na hora 23
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(pedido("1", "FINALIZADO", "10.00", dia(3, 23))));

        List<VendaPorHora> horas = service.vendasPorHora(INICIO, FIM, null, null);

        assertEquals(24, horas.size());
        assertEquals(1, horas.get(23).pedidos());
        assertEquals(0, horas.get(0).pedidos());
    }

    @Test
    void situacaoDosPedidosNormalizaStatusAntigosEIncluiCancelados() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(pedido("1", "PENDENTE", "10.00", dia(3, 12)),
                        pedido("2", "RECEBIDO", "10.00", dia(3, 13)),
                        pedido("3", "CANCELADO", "10.00", dia(3, 14)),
                        pedido("4", "ENTREGUE", "10.00", dia(3, 15))));

        SituacaoPedidos situacao = service.situacaoPedidos(INICIO, FIM, null, null);

        assertEquals(4, situacao.total());
        assertEquals(2, quantidade(situacao, "RECEBIDO"));
        assertEquals(1, quantidade(situacao, "CANCELADO"));
        assertEquals(1, quantidade(situacao, "FINALIZADO"));
        assertEquals(0, quantidade(situacao, "PRONTO"));
    }

    @Test
    void rankingOrdenaPorQuantidadeEPorReceitaERespeitaLimite() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(
                        pedido("1", "FINALIZADO", "70.00", dia(3, 12),
                                item("p1", "Pizza Calabresa", 1, "50.00"),
                                item("p2", "Refrigerante", 4, "5.00")),
                        pedido("2", "CANCELADO", "500.00", dia(3, 13),
                                item("p1", "Pizza Calabresa", 10, "50.00"))));

        List<ProdutoRanking> porQuantidade = service.produtosMaisVendidos(INICIO, FIM, null, null, 10, "quantidade");
        List<ProdutoRanking> porReceita = service.produtosMaisVendidos(INICIO, FIM, null, null, 1, "receita");

        assertEquals("Refrigerante", porQuantidade.get(0).nome());
        assertEquals(4, porQuantidade.get(0).quantidade());
        assertEquals(1, porQuantidade.get(0).posicao());
        assertEquals(1, porQuantidade.get(1).quantidade()); // pedido cancelado não conta
        assertEquals(1, porReceita.size());
        assertEquals("Pizza Calabresa", porReceita.get(0).nome());
    }

    @Test
    void filtroDeCategoriaRestringeItensEPedidos() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(
                        pedido("1", "FINALIZADO", "70.00", dia(3, 12),
                                item("p1", "Pizza Calabresa", 1, "50.00"),
                                item("p2", "Refrigerante", 1, "20.00")),
                        pedido("2", "FINALIZADO", "20.00", dia(3, 13), item("p2", "Refrigerante", 1, "20.00"))));

        List<VendaPorCategoria> categorias = service.vendasPorCategoria(INICIO, FIM, null, "pizza");
        List<ProdutoRanking> ranking = service.produtosMaisVendidos(INICIO, FIM, null, "Pizza", 10, null);

        assertEquals(1, categorias.size());
        assertEquals("Pizza", categorias.get(0).categoria());
        assertEquals(new BigDecimal("50.00"), categorias.get(0).faturamento());
        assertEquals(1, ranking.size());
        assertEquals("Pizza Calabresa", ranking.get(0).nome());
    }

    @Test
    void filtroDeStatusLimitaOsPedidos() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(Date.class), any(Date.class)))
                .thenReturn(List.of(pedido("1", "FINALIZADO", "50.00", dia(3, 12)),
                        pedido("2", "RECEBIDO", "30.00", dia(3, 13))))
                .thenReturn(List.of());

        Resumo resumo = service.resumo(INICIO, FIM, "finalizado", null);

        assertEquals(1, resumo.totalPedidos());
        assertEquals(new BigDecimal("50.00"), resumo.faturamentoTotal());
    }

    @Test
    void rejeitaParametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.resumo(FIM, INICIO, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.resumo(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 7), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.resumo(INICIO, FIM, "INEXISTENTE", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.vendasPorPeriodo(INICIO, FIM, null, null, "ano"));
        assertThrows(IllegalArgumentException.class,
                () -> service.produtosMaisVendidos(INICIO, FIM, null, null, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.produtosMaisVendidos(INICIO, FIM, null, null, 10, "preco"));
        assertThrows(IllegalArgumentException.class,
                () -> service.pedidosRecentes(INICIO, FIM, null, null, 51));
    }

    @Test
    void filtrosListamStatusECategorias() {
        when(produtoRepository.findAll()).thenReturn(List.of(
                produto("p1", "Pizza", "Pizza"), produto("p2", "Coca", "Bebida"), produto("p3", "X", "Pizza")));

        var filtros = service.filtros();

        assertTrue(filtros.status().contains("CANCELADO"));
        assertEquals(List.of("Bebida", "Pizza"), filtros.categorias());
        assertEquals("America/Sao_Paulo", filtros.fusoHorario());
    }

    // ---------------------------------------------------------------- apoio
    private long quantidade(SituacaoPedidos situacao, String status) {
        return situacao.status().stream().filter(s -> s.status().equals(status))
                .findFirst().orElseThrow().quantidade();
    }

    private Instant dia(int diaDoMes, int hora) {
        return LocalDate.of(2026, 11, diaDoMes).atTime(hora, 30).atZone(ZONA).toInstant();
    }

    private Pedido pedido(String id, String status, String total, LocalDate data, int hora) {
        return pedido(id, status, total, data.atTime(hora, 30).atZone(ZONA).toInstant());
    }

    private Pedido pedido(String id, String status, String total, Instant entrada, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setStatus(status);
        pedido.setValorTotal(new BigDecimal(total));
        pedido.setValorDesconto(BigDecimal.ZERO);
        pedido.setDataHoraEntrada(entrada);
        pedido.setItens(List.of(itens));
        return pedido;
    }

    private ItemPedido item(String produtoId, String nome, int quantidade, String precoUnitario) {
        BigDecimal preco = new BigDecimal(precoUnitario);
        return new ItemPedido(produtoId, nome, quantidade, preco, preco.multiply(BigDecimal.valueOf(quantidade)));
    }

    private Produto produto(String id, String nome, String tipo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(10.0);
        produto.setAtivo(true);
        return produto;
    }
}
