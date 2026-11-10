package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Gera pedidos fictícios dos últimos 60 dias para demonstrar o dashboard.
 * Só roda com o perfil "demo" (--spring.profiles.active=demo) e é idempotente.
 * Os pedidos de demonstração são gravados direto nos repositórios (sem passar pela auditoria)
 * e identificados por responsavelRegistro = "demo-seed".
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {
    static final String MARCADOR = "demo-seed";
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;

    public DemoDataSeeder(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
                          ClienteRepository clienteRepository) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
    }

    @Override
    public void run(String... args) {
        boolean jaPopulado = pedidoRepository.findAll().stream()
                .anyMatch(p -> MARCADOR.equals(p.getResponsavelRegistro()));
        if (jaPopulado) return;

        List<Produto> produtos = produtoRepository.findAll().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0)
                .collect(Collectors.toList());
        if (produtos.isEmpty()) {
            produtos = criarProdutos();
        }
        List<Cliente> clientes = clienteRepository.findAll();
        if (clientes.isEmpty()) {
            clientes = criarClientes();
        }

        Random random = new Random(42);
        List<Pedido> pedidos = new ArrayList<>();
        LocalDate hoje = LocalDate.now(ZONA);
        for (int dia = 59; dia >= 0; dia--) {
            LocalDate data = hoje.minusDays(dia);
            boolean fimDeSemana = data.getDayOfWeek().getValue() >= 5;
            int quantidade = (fimDeSemana ? 7 : 3) + random.nextInt(4);
            for (int i = 0; i < quantidade; i++) {
                pedidos.add(novoPedido(data, dia == 0, random, produtos, clientes));
            }
        }
        pedidoRepository.saveAll(pedidos);
        System.out.println("[demo] " + pedidos.size() + " pedidos de demonstração criados.");
    }

    private Pedido novoPedido(LocalDate data, boolean ehHoje, Random random, List<Produto> produtos,
                              List<Cliente> clientes) {
        int[] horas = {11, 12, 12, 13, 18, 19, 19, 20, 20, 21, 21, 22};
        ZonedDateTime entrada = data.atTime(horas[random.nextInt(horas.length)], random.nextInt(60))
                .atZone(ZONA);
        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int quantidadeTotal = 0;
        int linhas = 1 + random.nextInt(3);
        for (int i = 0; i < linhas; i++) {
            // produtos "populares" (início da lista) saem mais
            int indice = (int) Math.min(produtos.size() - 1, Math.abs(random.nextGaussian()) * produtos.size() / 2.5);
            Produto produto = produtos.get(indice);
            int quantidade = 1 + random.nextInt(2);
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal valor = preco.multiply(BigDecimal.valueOf(quantidade));
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, preco, valor));
            subtotal = subtotal.add(valor);
            quantidadeTotal += quantidade;
        }
        BigDecimal percentual = random.nextInt(10) == 0 ? new BigDecimal("10.00") : BigDecimal.ZERO.setScale(2);
        BigDecimal desconto = subtotal.multiply(percentual).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        String status;
        int sorteio = random.nextInt(100);
        if (ehHoje) {
            String[] hojeStatus = {"RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA", "FINALIZADO"};
            status = hojeStatus[random.nextInt(hojeStatus.length)];
        } else if (sorteio < 6) {
            status = "CANCELADO";
        } else {
            status = "FINALIZADO";
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(clientes.get(random.nextInt(clientes.size())).getId());
        pedido.setItens(itens);
        pedido.setQuantidade(quantidadeTotal);
        pedido.setTipoPizza(itens.stream().map(ItemPedido::getNomeProduto).collect(Collectors.joining(", ")));
        pedido.setSabor(pedido.getTipoPizza());
        pedido.setStatus(status);
        pedido.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        pedido.setDescontoPercentual(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
        pedido.setResponsavelRegistro(MARCADOR);
        pedido.setDataHoraEntrada(entrada.toInstant());
        if (status.equals("CANCELADO")) {
            pedido.setJustificativaCancelamento("Pedido de demonstração cancelado.");
        } else if (status.equals("SAIU_PARA_ENTREGA") || status.equals("FINALIZADO")) {
            pedido.setDataHoraSaida(entrada.plusMinutes(40).toInstant());
            pedido.setResponsavelSaida(MARCADOR);
        }
        return pedido;
    }

    private List<Produto> criarProdutos() {
        Object[][] dados = {
                {"Pizza Calabresa", "Pizza", 48.0}, {"Pizza Mussarela", "Pizza", 44.0},
                {"Pizza Frango com Catupiry", "Pizza", 52.0}, {"Esfiha de Carne", "Esfiha", 7.5},
                {"Esfiha de Queijo", "Esfiha", 7.0}, {"Refrigerante 2L", "Bebida", 12.0},
                {"Suco Natural", "Bebida", 9.0}, {"X-Burger", "Lanche", 22.0}};
        List<Produto> criados = new ArrayList<>();
        for (Object[] d : dados) {
            Produto produto = new Produto();
            produto.setNome((String) d[0]);
            produto.setTipo((String) d[1]);
            produto.setPreco((Double) d[2]);
            produto.setAtivo(true);
            criados.add(produtoRepository.save(produto));
        }
        return criados;
    }

    private List<Cliente> criarClientes() {
        List<Cliente> criados = new ArrayList<>();
        String[] nomes = {"Cliente Demo 1", "Cliente Demo 2", "Cliente Demo 3", "Cliente Demo 4"};
        for (int i = 0; i < nomes.length; i++) {
            Cliente cliente = new Cliente();
            cliente.setNome(nomes[i]);
            cliente.setTelefone("(11) 90000-000" + i);
            criados.add(clienteRepository.save(cliente));
        }
        return criados;
    }
}
