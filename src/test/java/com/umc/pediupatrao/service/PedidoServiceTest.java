package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class PedidoServiceTest {
    private PedidoRepository pedidoRepository;
    private ProdutoRepository produtoRepository;
    private ClienteRepository clienteRepository;
    private PedidoService pedidoService;

    @BeforeEach
    void configurar() {
        pedidoRepository = org.mockito.Mockito.mock(PedidoRepository.class);
        produtoRepository = org.mockito.Mockito.mock(ProdutoRepository.class);
        clienteRepository = org.mockito.Mockito.mock(ClienteRepository.class);
        RegistroAuditoriaRepository auditoriaRepository =
                org.mockito.Mockito.mock(RegistroAuditoriaRepository.class);
        AuditoriaService auditoria = new AuditoriaService(auditoriaRepository);
        pedidoService = new PedidoService(pedidoRepository, produtoRepository, clienteRepository, auditoria);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clienteRepository.findById("cliente-1")).thenReturn(Optional.of(new com.umc.pediupatrao.entity.Cliente()));
    }

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criaPedidoCalculandoPrecoNoServidor() {
        autenticar("atendente01", "ATENDENTE");
        Produto produto = produto("pizza-1", "Pizza", 12.50, true);
        when(produtoRepository.findById("pizza-1")).thenReturn(Optional.of(produto));

        CriarPedidoRequest request = new CriarPedidoRequest();
        request.setClienteId("cliente-1");
        request.setItens(List.of(item("pizza-1", 2)));
        Pedido criado = pedidoService.criarPedido(request);

        assertEquals(new BigDecimal("25.00"), criado.getSubtotal());
        assertEquals(new BigDecimal("25.00"), criado.getValorTotal());
        assertEquals(2, criado.getQuantidade());
        assertEquals("RECEBIDO", criado.getStatus());
        assertEquals("atendente01", criado.getResponsavelRegistro());
        assertNotNull(criado.getDataHoraEntrada());
    }

    @Test
    void recusaTransicaoForaDoFluxo() {
        autenticar("atendente01", "ATENDENTE");
        Pedido pedido = pedido("RECEBIDO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.atualizarStatus("pedido-1", "FINALIZADO"));
    }

    @Test
    void permiteFinalizarAposRegistrarSaida() {
        autenticar("atendente01", "ATENDENTE");
        Pedido pedido = pedido("SAIU_PARA_ENTREGA");
        pedido.setDataHoraSaida(Instant.now());
        pedido.setResponsavelSaida("atendente01");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));

        Pedido atualizado = pedidoService.atualizarStatus("pedido-1", "FINALIZADO");

        assertEquals("FINALIZADO", atualizado.getStatus());
    }

    @Test
    void somenteGerenteAplicaDescontoDeAteVintePorCento() {
        autenticar("admin", "ADMIN");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido("RECEBIDO")));
        assertThrows(AccessDeniedException.class,
                () -> pedidoService.aplicarDesconto("pedido-1", new BigDecimal("10")));

        autenticar("gerente01", "GERENTE");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido("RECEBIDO")));
        Pedido atualizado = pedidoService.aplicarDesconto("pedido-1", new BigDecimal("20"));
        assertEquals(new BigDecimal("20.00"), atualizado.getValorDesconto());
        assertEquals(new BigDecimal("80.00"), atualizado.getValorTotal());
        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.aplicarDesconto("pedido-1", new BigDecimal("20.01")));
    }

    @Test
    void exigeJustificativaEImpedeCancelarPedidoQueSaiu() {
        autenticar("admin", "ADMIN");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido("PRONTO")));
        assertThrows(AccessDeniedException.class,
                () -> pedidoService.cancelarPedido("pedido-1", "Motivo informado"));

        autenticar("gerente01", "GERENTE");
        Pedido pedido = pedido("PRONTO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.cancelarPedido("pedido-1", " "));
        Pedido cancelado = pedidoService.cancelarPedido("pedido-1", "Cliente desistiu");
        assertEquals("CANCELADO", cancelado.getStatus());
        assertEquals("Cliente desistiu", cancelado.getJustificativaCancelamento());
        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.atualizarStatus("pedido-1", "PRONTO"));

        Pedido emRota = pedido("SAIU_PARA_ENTREGA");
        when(pedidoRepository.findById("pedido-2")).thenReturn(Optional.of(emRota));
        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.cancelarPedido("pedido-2", "Atraso"));
    }

    private void autenticar(String usuario, String role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                usuario, "irrelevante", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setPreco(preco);
        produto.setAtivo(ativo);
        return produto;
    }

    private ItemPedidoRequest item(String produtoId, int quantidade) {
        ItemPedidoRequest item = new ItemPedidoRequest();
        item.setProdutoId(produtoId);
        item.setQuantidade(quantidade);
        return item;
    }

    private Pedido pedido(String status) {
        Pedido pedido = new Pedido();
        pedido.setId("pedido-1");
        pedido.setStatus(status);
        pedido.setSubtotal(new BigDecimal("100.00"));
        pedido.setValorTotal(new BigDecimal("100.00"));
        pedido.setDescontoPercentual(BigDecimal.ZERO);
        pedido.setValorDesconto(BigDecimal.ZERO);
        return pedido;
    }
}
