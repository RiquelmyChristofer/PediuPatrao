package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.entity.CampoAuditado;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {
    private static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    private static final BigDecimal CEM = new BigDecimal("100");

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
                        ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    public Pedido criarPedido(CriarPedidoRequest request) {
        if (request == null || request.getClienteId() == null || request.getClienteId().isBlank()) {
            throw new IllegalArgumentException("O cliente do pedido é obrigatório.");
        }
        if (clienteRepository.findById(request.getClienteId()).isEmpty()) {
            throw new IllegalArgumentException("Cliente não encontrado.");
        }
        if (request.getItens() == null || request.getItens().isEmpty()) {
            throw new IllegalArgumentException("O pedido precisa conter ao menos um item.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        long quantidadeTotal = 0;
        for (ItemPedidoRequest itemRequest : request.getItens()) {
            if (itemRequest == null || itemRequest.getProdutoId() == null
                    || itemRequest.getProdutoId().isBlank() || itemRequest.getQuantidade() <= 0) {
                throw new IllegalArgumentException("Cada item precisa de um produto válido e quantidade maior que zero.");
            }
            Produto produto = produtoRepository.findById(itemRequest.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new IllegalArgumentException("O produto " + produto.getNome() + " está inativo.");
            }
            if (produto.getNome() == null || produto.getNome().isBlank()) {
                throw new IllegalArgumentException("O produto cadastrado não possui nome válido.");
            }
            if (produto.getPreco() == null || !Double.isFinite(produto.getPreco())
                    || produto.getPreco() <= 0) {
                throw new IllegalArgumentException("O produto " + produto.getNome() + " não possui preço válido.");
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal valorItem = preco.multiply(BigDecimal.valueOf(itemRequest.getQuantidade()))
                    .setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(),
                    itemRequest.getQuantidade(), preco, valorItem));
            subtotal = subtotal.add(valorItem);
            quantidadeTotal += itemRequest.getQuantidade();
            if (quantidadeTotal > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("A quantidade total do pedido excede o limite permitido.");
            }
        }

        Instant agora = Instant.now();
        Pedido pedido = new Pedido();
        pedido.setClienteId(request.getClienteId());
        pedido.setItens(itens);
        pedido.setQuantidade((int) quantidadeTotal);
        pedido.setTipoPizza(itens.stream().map(ItemPedido::getNomeProduto).collect(Collectors.joining(", ")));
        pedido.setSabor(pedido.getTipoPizza());
        pedido.setStatus("RECEBIDO");
        pedido.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        pedido.setDescontoPercentual(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        pedido.setValorDesconto(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        pedido.setValorTotal(pedido.getSubtotal());
        pedido.setResponsavelRegistro(auditoriaService.usuarioAtual());
        pedido.setDataHoraEntrada(agora);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("CRIACAO", "PEDIDO", salvo.getId(), "Pedido registrado.",
                List.of(
                        new CampoAuditado("clienteId", null, salvo.getClienteId()),
                        new CampoAuditado("itens", null, resumoItens(itens)),
                        new CampoAuditado("quantidade", null, quantidadeTotal),
                        new CampoAuditado("status", null, salvo.getStatus()),
                        new CampoAuditado("subtotal", null, salvo.getSubtotal()),
                        new CampoAuditado("valorTotal", null, salvo.getValorTotal()),
                        new CampoAuditado("responsavelRegistro", null, salvo.getResponsavelRegistro()),
                        new CampoAuditado("dataHoraEntrada", null, agora)));
        return salvo;
    }

    public List<Pedido> listarPedidos() {
        List<Pedido> pedidos = pedidoRepository.findAll();
        pedidos.forEach(pedido -> pedido.setStatus(normalizarStatus(pedido.getStatus())));
        return pedidos;
    }

    public Pedido atualizarStatus(String id, String status) {
        Pedido pedido = buscarPedido(id);
        String anterior = normalizarStatus(pedido.getStatus());
        String novo = normalizarStatus(status);
        if (!statusPermitido(novo)) {
            throw new IllegalArgumentException("Status inválido: " + status);
        }
        if (novo.equals("FINALIZADO")
                && (pedido.getDataHoraSaida() == null || pedido.getResponsavelSaida() == null
                || pedido.getResponsavelSaida().isBlank())) {
            throw new IllegalArgumentException("Não é possível finalizar sem registrar data/hora e responsável pela saída.");
        }
        if (!transicaoPermitida(anterior, novo)) {
            throw new IllegalArgumentException("Não é permitida a transição de " + anterior + " para " + novo + ".");
        }

        Instant agora = Instant.now();
        List<CampoAuditado> alteracoes = new ArrayList<>();
        alteracoes.add(new CampoAuditado("status", anterior, novo));
        pedido.setStatus(novo);
        if (novo.equals("SAIU_PARA_ENTREGA") || novo.equals("RETIRADO")) {
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(auditoriaService.usuarioAtual());
            alteracoes.add(new CampoAuditado("dataHoraSaida", null, agora));
            alteracoes.add(new CampoAuditado("responsavelSaida", null, pedido.getResponsavelSaida()));
        }
        Pedido atualizado = pedidoRepository.save(pedido);
        auditoriaService.registrar("ALTERACAO_STATUS", "PEDIDO", id,
                "Status alterado de " + anterior + " para " + novo + ".", alteracoes);
        return atualizado;
    }

    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        auditoriaService.exigirGerente();
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) < 0
                || percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("O desconto deve ser percentual entre 0% e 20%.");
        }
        percentual = percentual.setScale(2, RoundingMode.HALF_UP);
        Pedido pedido = buscarPedido(id);
        exigirPedidoAntesDaSaida(pedido);
        if (pedido.getSubtotal() == null) {
            throw new IllegalArgumentException(
                    "Este pedido antigo não possui subtotal registrado e não aceita desconto.");
        }

        BigDecimal valorDesconto = pedido.getSubtotal()
                .multiply(percentual)
                .divide(CEM, 2, RoundingMode.HALF_UP);
        BigDecimal total = pedido.getSubtotal().subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP);
        BigDecimal descontoAnterior = valorOuZero(pedido.getDescontoPercentual());
        BigDecimal valorAnterior = valorOuZero(pedido.getValorDesconto());
        BigDecimal totalAnterior = valorOuZero(pedido.getValorTotal());

        pedido.setDescontoPercentual(percentual.setScale(2, RoundingMode.HALF_UP));
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(total);
        Pedido atualizado = pedidoRepository.save(pedido);
        auditoriaService.registrar("DESCONTO", "PEDIDO", id,
                "Desconto percentual aplicado pelo GERENTE.",
                List.of(
                        new CampoAuditado("descontoPercentual", descontoAnterior, pedido.getDescontoPercentual()),
                        new CampoAuditado("valorDesconto", valorAnterior, valorDesconto),
                        new CampoAuditado("valorTotal", totalAnterior, total)));
        return atualizado;
    }

    public Pedido cancelarPedido(String id, String justificativa) {
        auditoriaService.exigirGerente();
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("A justificativa do cancelamento é obrigatória.");
        }
        String motivo = justificativa.trim();
        if (motivo.length() > 500) {
            throw new IllegalArgumentException("A justificativa deve ter no máximo 500 caracteres.");
        }
        Pedido pedido = buscarPedido(id);
        String atual = normalizarStatus(pedido.getStatus());
        if (atual.equals("CANCELADO") || atual.equals("FINALIZADO")
                || atual.equals("SAIU_PARA_ENTREGA") || atual.equals("RETIRADO")) {
            throw new IllegalArgumentException("O pedido não pode ser cancelado após a saída, finalização ou cancelamento.");
        }
        if (pedido.getDataHoraSaida() != null) {
            throw new IllegalArgumentException("O pedido não pode ser cancelado depois de registrar a saída.");
        }
        pedido.setStatus("CANCELADO");
        pedido.setJustificativaCancelamento(motivo);
        Pedido atualizado = pedidoRepository.save(pedido);
        auditoriaService.registrar("CANCELAMENTO", "PEDIDO", id,
                "Pedido cancelado pelo GERENTE com justificativa.",
                List.of(
                        new CampoAuditado("status", atual, "CANCELADO"),
                        new CampoAuditado("justificativaCancelamento", null, motivo)));
        return atualizado;
    }

    private Pedido buscarPedido(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
    }

    private void exigirPedidoAntesDaSaida(Pedido pedido) {
        String status = normalizarStatus(pedido.getStatus());
        if (status.equals("SAIU_PARA_ENTREGA") || status.equals("RETIRADO")
                || status.equals("FINALIZADO") || status.equals("CANCELADO")) {
            throw new IllegalArgumentException("Descontos e cancelamentos só podem ocorrer antes da saída do pedido.");
        }
        if (pedido.getDataHoraSaida() != null) {
            throw new IllegalArgumentException("Descontos e cancelamentos só podem ocorrer antes da saída do pedido.");
        }
    }

    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String resumoItens(List<ItemPedido> itens) {
        return itens.stream()
                .map(item -> item.getNomeProduto() + " x" + item.getQuantidade()
                        + " (R$ " + item.getPrecoUnitario() + ")")
                .collect(Collectors.joining("; "));
    }

    private String normalizarStatus(String status) {
        if (status == null || status.isBlank()) return "RECEBIDO";
        String normalizado = status.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_').replace('-', '_');
        return switch (normalizado) {
            case "PENDENTE", "NOVO" -> "RECEBIDO";
            case "EM_PREPARO" -> "EM_PREPARACAO";
            case "EM_ROTA", "EM_ENTREGA" -> "SAIU_PARA_ENTREGA";
            case "ENTREGUE" -> "FINALIZADO";
            default -> normalizado;
        };
    }

    private boolean statusPermitido(String status) {
        return List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA",
                "RETIRADO", "FINALIZADO").contains(status);
    }

    private boolean transicaoPermitida(String atual, String novo) {
        if (atual.equals(novo) || atual.equals("CANCELADO") || atual.equals("FINALIZADO")) {
            return false;
        }
        return switch (atual) {
            case "RECEBIDO" -> novo.equals("EM_PREPARACAO");
            case "EM_PREPARACAO" -> novo.equals("PRONTO");
            case "PRONTO" -> novo.equals("SAIU_PARA_ENTREGA") || novo.equals("RETIRADO");
            case "SAIU_PARA_ENTREGA", "RETIRADO" -> novo.equals("FINALIZADO");
            default -> false;
        };
    }
}
