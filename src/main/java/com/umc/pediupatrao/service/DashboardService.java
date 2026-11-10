package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDtos.Comparativo;
import com.umc.pediupatrao.dto.DashboardDtos.Filtros;
import com.umc.pediupatrao.dto.DashboardDtos.PedidoRecente;
import com.umc.pediupatrao.dto.DashboardDtos.Periodo;
import com.umc.pediupatrao.dto.DashboardDtos.PontoVenda;
import com.umc.pediupatrao.dto.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDtos.Resumo;
import com.umc.pediupatrao.dto.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dto.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dto.DashboardDtos.StatusContagem;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPorCategoria;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPorHora;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Consultas agregadas (somente leitura) que alimentam o dashboard administrativo.
 *
 * <p>Regras de negócio adotadas:
 * <ul>
 *   <li>O período é filtrado pela data/hora de ENTRADA do pedido, no fuso configurado.</li>
 *   <li>"Venda válida" = pedido com status diferente de CANCELADO. Faturamento, ticket médio,
 *       gráficos de venda e ranking consideram apenas vendas válidas.</li>
 *   <li>A situação dos pedidos considera todos os status (inclusive CANCELADO).</li>
 *   <li>Valores monetários usam duas casas decimais (HALF_UP).</li>
 * </ul>
 */
@Service
public class DashboardService {
    public static final int PERIODO_MAXIMO_DIAS = 366;
    public static final int PERIODO_PADRAO_DIAS = 30;
    public static final int LIMITE_MAXIMO = 50;
    static final String SEM_CATEGORIA = "Sem categoria";
    static final List<String> STATUS_VALIDOS = List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO",
            "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO");
    private static final BigDecimal CEM = new BigDecimal("100");

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final Clock clock;
    private final ZoneId zona;

    @Autowired
    public DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
                            ClienteRepository clienteRepository,
                            @Value("${app.dashboard.timezone:America/Sao_Paulo}") String fusoHorario) {
        this(pedidoRepository, produtoRepository, clienteRepository, Clock.system(ZoneId.of(fusoHorario)));
    }

    DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
                     ClienteRepository clienteRepository, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.clock = clock;
        this.zona = clock.getZone();
    }

    // ------------------------------------------------------------------ RF01
    public Resumo resumo(LocalDate inicio, LocalDate fim, String status, String categoria) {
        Filtro filtro = resolverFiltro(inicio, fim, status, categoria);
        Metricas atual = calcular(carregar(filtro));
        Metricas anterior = calcular(carregar(periodoAnterior(filtro)));

        BigDecimal ticket = atual.validos() == 0 ? zero()
                : atual.faturamento().divide(BigDecimal.valueOf(atual.validos()), 2, RoundingMode.HALF_UP);
        BigDecimal taxaCancelamento = atual.total() == 0 ? zero()
                : BigDecimal.valueOf(atual.cancelados()).multiply(CEM)
                        .divide(BigDecimal.valueOf(atual.total()), 2, RoundingMode.HALF_UP);
        Comparativo comparativo = new Comparativo(anterior.faturamento(),
                variacao(atual.faturamento(), anterior.faturamento()),
                anterior.validos(),
                variacao(BigDecimal.valueOf(atual.validos()), BigDecimal.valueOf(anterior.validos())));
        return new Resumo(periodo(filtro), atual.faturamento(), atual.total(), atual.validos(),
                atual.cancelados(), taxaCancelamento, ticket, atual.descontos(), atual.itens(), comparativo);
    }

    // ------------------------------------------------------------------ RF02
    public SerieVendas vendasPorPeriodo(LocalDate inicio, LocalDate fim, String status, String categoria,
                                        String agrupamento) {
        String agrupar = agrupamento == null || agrupamento.isBlank() ? "dia"
                : agrupamento.trim().toLowerCase(Locale.ROOT);
        if (!List.of("dia", "semana", "mes").contains(agrupar)) {
            throw new IllegalArgumentException("Agrupamento inválido. Use dia, semana ou mes.");
        }
        Filtro filtro = resolverFiltro(inicio, fim, status, categoria);
        Map<String, Long> quantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        for (LocalDate dia = filtro.inicio(); !dia.isAfter(filtro.fim()); dia = dia.plusDays(1)) {
            String chave = chave(dia, agrupar);
            quantidades.putIfAbsent(chave, 0L);
            valores.putIfAbsent(chave, zero());
        }
        for (Pedido pedido : validos(carregar(filtro).pedidos())) {
            String chave = chave(dataEntrada(pedido), agrupar);
            quantidades.merge(chave, 1L, Long::sum);
            valores.merge(chave, valor(pedido), BigDecimal::add);
        }
        List<PontoVenda> pontos = new ArrayList<>();
        quantidades.forEach((chave, qtd) -> pontos.add(new PontoVenda(chave, qtd, valores.get(chave))));
        return new SerieVendas(periodo(filtro), agrupar, pontos);
    }

    public List<VendaPorHora> vendasPorHora(LocalDate inicio, LocalDate fim, String status, String categoria) {
        Filtro filtro = resolverFiltro(inicio, fim, status, categoria);
        long[] quantidades = new long[24];
        BigDecimal[] valores = new BigDecimal[24];
        for (int h = 0; h < 24; h++) valores[h] = zero();
        for (Pedido pedido : validos(carregar(filtro).pedidos())) {
            int hora = pedido.getDataHoraEntrada().atZone(zona).getHour();
            quantidades[hora]++;
            valores[hora] = valores[hora].add(valor(pedido));
        }
        List<VendaPorHora> resultado = new ArrayList<>();
        for (int h = 0; h < 24; h++) resultado.add(new VendaPorHora(h, quantidades[h], valores[h]));
        return resultado;
    }

    public List<VendaPorCategoria> vendasPorCategoria(LocalDate inicio, LocalDate fim, String status,
                                                      String categoria) {
        Filtro filtro = resolverFiltro(inicio, fim, status, categoria);
        Dados dados = carregar(filtro);
        Map<String, long[]> quantidades = new HashMap<>();
        Map<String, BigDecimal> valores = new HashMap<>();
        for (Pedido pedido : validos(dados.pedidos())) {
            for (ItemPedido item : itensConsiderados(pedido, dados)) {
                String cat = categoriaDe(item, dados.categorias());
                quantidades.computeIfAbsent(cat, k -> new long[1])[0] += item.getQuantidade();
                valores.merge(cat, valorItem(item), BigDecimal::add);
            }
        }
        return valores.entrySet().stream()
                .map(e -> new VendaPorCategoria(e.getKey(), quantidades.get(e.getKey())[0], e.getValue()))
                .sorted(Comparator.comparing(VendaPorCategoria::faturamento).reversed()
                        .thenComparing(VendaPorCategoria::categoria))
                .toList();
    }

    // ------------------------------------------------------------------ RF03
    public SituacaoPedidos situacaoPedidos(LocalDate inicio, LocalDate fim, String status, String categoria) {
        Filtro filtro = resolverFiltro(inicio, fim, status, categoria);
        List<Pedido> pedidos = carregar(filtro).pedidos();
        Map<String, Long> contagem = new LinkedHashMap<>();
        STATUS_VALIDOS.forEach(s -> contagem.put(s, 0L));
        pedidos.forEach(p -> contagem.merge(statusDe(p), 1L, Long::sum));
        List<StatusContagem> lista = contagem.entrySet().stream()
                .map(e -> new StatusContagem(e.getKey(), e.getValue())).toList();
        return new SituacaoPedidos(periodo(filtro), pedidos.size(), lista);
    }

    public List<PedidoRecente> pedidosRecentes(LocalDate inicio, LocalDate fim, String status,
                                               String categoria, Integer limite) {
        int max = validarLimite(limite);
        Filtro filtro = resolverFiltro(inicio, fim, status, categoria);
        List<Pedido> recentes = carregar(filtro).pedidos().stream()
                .sorted(Comparator.comparing(Pedido::getDataHoraEntrada).reversed())
                .limit(max).toList();
        Set<String> clienteIds = recentes.stream().map(Pedido::getClienteId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> nomes = new HashMap<>();
        if (!clienteIds.isEmpty()) {
            for (Cliente cliente : clienteRepository.findAllById(clienteIds)) {
                nomes.put(cliente.getId(), cliente.getNome());
            }
        }
        return recentes.stream().map(p -> new PedidoRecente(p.getId(),
                nomes.getOrDefault(p.getClienteId(), "Cliente não encontrado"), statusDe(p), valor(p),
                p.getDataHoraEntrada(), p.getResponsavelRegistro(), resumoItens(p))).toList();
    }

    // ------------------------------------------------------------------ RF04
    public List<ProdutoRanking> produtosMaisVendidos(LocalDate inicio, LocalDate fim, String status,
                                                     String categoria, Integer limite, String ordenarPor) {
        int max = validarLimite(limite);
        String criterio = ordenarPor == null || ordenarPor.isBlank() ? "quantidade"
                : ordenarPor.trim().toLowerCase(Locale.ROOT);
        if (!criterio.equals("quantidade") && !criterio.equals("receita")) {
            throw new IllegalArgumentException("Critério de ordenação inválido. Use quantidade ou receita.");
        }
        Filtro filtro = resolverFiltro(inicio, fim, status, categoria);
        Dados dados = carregar(filtro);
        Map<String, Acumulador> acumulados = new LinkedHashMap<>();
        for (Pedido pedido : validos(dados.pedidos())) {
            for (ItemPedido item : itensConsiderados(pedido, dados)) {
                String nome = item.getNomeProduto() == null || item.getNomeProduto().isBlank()
                        ? "(produto sem nome)" : item.getNomeProduto();
                String chave = item.getProdutoId() != null ? item.getProdutoId() : nome;
                Acumulador acc = acumulados.computeIfAbsent(chave,
                        k -> new Acumulador(item.getProdutoId(), nome, categoriaDe(item, dados.categorias())));
                acc.quantidade += item.getQuantidade();
                acc.receita = acc.receita.add(valorItem(item));
            }
        }
        Comparator<Acumulador> porQuantidade = Comparator.comparingLong((Acumulador a) -> a.quantidade).reversed();
        Comparator<Acumulador> porReceita = Comparator.comparing((Acumulador a) -> a.receita).reversed();
        Comparator<Acumulador> comparador = criterio.equals("quantidade")
                ? porQuantidade.thenComparing(porReceita) : porReceita.thenComparing(porQuantidade);
        List<Acumulador> ordenados = acumulados.values().stream()
                .sorted(comparador.thenComparing(a -> a.nome)).limit(max).toList();
        List<ProdutoRanking> ranking = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            Acumulador a = ordenados.get(i);
            ranking.add(new ProdutoRanking(i + 1, a.produtoId, a.nome, a.categoria, a.quantidade, a.receita));
        }
        return ranking;
    }

    // ------------------------------------------------------------------ RF05
    public Filtros filtros() {
        Set<String> categorias = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Produto produto : produtoRepository.findAll()) {
            if (produto.getTipo() != null && !produto.getTipo().isBlank()) {
                categorias.add(produto.getTipo().trim());
            }
        }
        return new Filtros(STATUS_VALIDOS, List.copyOf(categorias), zona.getId(),
                PERIODO_MAXIMO_DIAS, PERIODO_PADRAO_DIAS);
    }

    // ------------------------------------------------------------ internos
    private record Filtro(LocalDate inicio, LocalDate fim, String status, String categoria) {}

    private record Dados(List<Pedido> pedidos, Map<String, String> categorias, String categoriaFiltro) {}

    private record Metricas(long total, long validos, long cancelados, BigDecimal faturamento,
                            BigDecimal descontos, long itens) {}

    private static final class Acumulador {
        final String produtoId;
        final String nome;
        final String categoria;
        long quantidade;
        BigDecimal receita = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Acumulador(String produtoId, String nome, String categoria) {
            this.produtoId = produtoId;
            this.nome = nome;
            this.categoria = categoria;
        }
    }

    private Filtro resolverFiltro(LocalDate inicio, LocalDate fim, String status, String categoria) {
        LocalDate fimEfetivo = fim != null ? fim : LocalDate.now(clock);
        LocalDate inicioEfetivo = inicio != null ? inicio : fimEfetivo.minusDays(PERIODO_PADRAO_DIAS - 1L);
        if (inicioEfetivo.isAfter(fimEfetivo)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(inicioEfetivo, fimEfetivo) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("O período máximo de consulta é de " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        String statusEfetivo = null;
        if (status != null && !status.isBlank()) {
            statusEfetivo = normalizarStatus(status);
            if (!STATUS_VALIDOS.contains(statusEfetivo)) {
                throw new IllegalArgumentException("Status inválido. Valores aceitos: "
                        + String.join(", ", STATUS_VALIDOS) + ".");
            }
        }
        String categoriaEfetiva = categoria == null || categoria.isBlank() ? null : categoria.trim();
        return new Filtro(inicioEfetivo, fimEfetivo, statusEfetivo, categoriaEfetiva);
    }

    private Filtro periodoAnterior(Filtro filtro) {
        long dias = ChronoUnit.DAYS.between(filtro.inicio(), filtro.fim()) + 1;
        return new Filtro(filtro.inicio().minusDays(dias), filtro.inicio().minusDays(1),
                filtro.status(), filtro.categoria());
    }

    private Periodo periodo(Filtro filtro) {
        return new Periodo(filtro.inicio(), filtro.fim());
    }

    private Dados carregar(Filtro filtro) {
        Date de = Date.from(filtro.inicio().atStartOfDay(zona).toInstant());
        Date ate = Date.from(filtro.fim().plusDays(1).atStartOfDay(zona).toInstant());
        List<Pedido> todos = pedidoRepository.buscarPorPeriodoDeEntrada(de, ate);
        if (todos == null) todos = List.of();
        todos = todos.stream().filter(p -> p.getDataHoraEntrada() != null).toList();
        Map<String, String> categorias = mapearCategorias(todos);
        Dados semFiltroDeItem = new Dados(todos, categorias, filtro.categoria());
        List<Pedido> filtrados = todos.stream()
                .filter(p -> filtro.status() == null || filtro.status().equals(statusDe(p)))
                .filter(p -> filtro.categoria() == null
                        || !itensConsiderados(p, semFiltroDeItem).isEmpty())
                .toList();
        return new Dados(filtrados, categorias, filtro.categoria());
    }

    private Map<String, String> mapearCategorias(List<Pedido> pedidos) {
        Set<String> ids = pedidos.stream().flatMap(p -> itens(p).stream())
                .map(ItemPedido::getProdutoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> mapa = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Produto produto : produtoRepository.findAllById(ids)) {
                String tipo = produto.getTipo() == null || produto.getTipo().isBlank()
                        ? SEM_CATEGORIA : produto.getTipo().trim();
                mapa.put(produto.getId(), tipo);
            }
        }
        return mapa;
    }

    private Metricas calcular(Dados dados) {
        List<Pedido> todos = dados.pedidos();
        List<Pedido> validos = validos(todos);
        BigDecimal faturamento = validos.stream().map(this::valor).reduce(zero(), BigDecimal::add);
        BigDecimal descontos = validos.stream()
                .map(p -> p.getValorDesconto() == null ? zero() : escala(p.getValorDesconto()))
                .reduce(zero(), BigDecimal::add);
        long itens = validos.stream().flatMap(p -> itensConsiderados(p, dados).stream())
                .mapToLong(ItemPedido::getQuantidade).sum();
        return new Metricas(todos.size(), validos.size(), todos.size() - validos.size(),
                faturamento, descontos, itens);
    }

    private List<Pedido> validos(List<Pedido> pedidos) {
        return pedidos.stream().filter(p -> !"CANCELADO".equals(statusDe(p))).toList();
    }

    private List<ItemPedido> itens(Pedido pedido) {
        return pedido.getItens() == null ? List.of() : pedido.getItens();
    }

    /** Itens que entram nas métricas: todos, ou apenas os da categoria filtrada. */
    private List<ItemPedido> itensConsiderados(Pedido pedido, Dados dados) {
        if (dados.categoriaFiltro() == null) return itens(pedido);
        return itens(pedido).stream()
                .filter(i -> dados.categoriaFiltro().equalsIgnoreCase(categoriaDe(i, dados.categorias())))
                .toList();
    }

    private String categoriaDe(ItemPedido item, Map<String, String> categorias) {
        return categorias.getOrDefault(item.getProdutoId(), SEM_CATEGORIA);
    }

    private BigDecimal valor(Pedido pedido) {
        return pedido.getValorTotal() == null ? zero() : escala(pedido.getValorTotal());
    }

    private BigDecimal valorItem(ItemPedido item) {
        if (item.getSubtotal() != null) return escala(item.getSubtotal());
        if (item.getPrecoUnitario() != null) {
            return escala(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        return zero();
    }

    private LocalDate dataEntrada(Pedido pedido) {
        return pedido.getDataHoraEntrada().atZone(zona).toLocalDate();
    }

    private String chave(LocalDate data, String agrupamento) {
        return switch (agrupamento) {
            case "semana" -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case "mes" -> YearMonth.from(data).toString();
            default -> data.toString();
        };
    }

    private String resumoItens(Pedido pedido) {
        String texto = itens(pedido).stream()
                .map(i -> i.getNomeProduto() + " x" + i.getQuantidade())
                .collect(Collectors.joining(", "));
        return texto.length() > 120 ? texto.substring(0, 117) + "..." : texto;
    }

    private int validarLimite(Integer limite) {
        int valor = limite == null ? 10 : limite;
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return valor;
    }

    private BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) return null;
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String statusDe(Pedido pedido) {
        return normalizarStatus(pedido.getStatus());
    }

    private String normalizarStatus(String status) {
        if (status == null || status.isBlank()) return "RECEBIDO";
        String normalizado = status.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (normalizado) {
            case "PENDENTE", "NOVO" -> "RECEBIDO";
            case "EM_PREPARO" -> "EM_PREPARACAO";
            case "EM_ROTA", "EM_ENTREGA" -> "SAIU_PARA_ENTREGA";
            case "ENTREGUE" -> "FINALIZADO";
            default -> normalizado;
        };
    }
}
