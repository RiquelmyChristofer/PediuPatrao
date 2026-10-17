package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CancelamentoPedidoRequest;
import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.dto.DescontoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody CriarPedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(request));
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody DescontoPedidoRequest request) {
        return pedidoService.aplicarDesconto(id, request.getPercentual());
    }

    @PostMapping("/{id}/cancelamento")
    public Pedido cancelarPedido(@PathVariable String id, @RequestBody CancelamentoPedidoRequest request) {
        return pedidoService.cancelarPedido(id, request.getJustificativa());
    }
}
