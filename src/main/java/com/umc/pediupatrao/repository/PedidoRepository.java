package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.Date;
import java.util.List;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    /** Pedidos cuja entrada ocorreu em [inicio, fim). Usado pelo dashboard administrativo. */
    @Query("{ 'dataHoraEntrada': { $gte: ?0, $lt: ?1 } }")
    List<Pedido> buscarPorPeriodoDeEntrada(Date inicio, Date fim);
}
