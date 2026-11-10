package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RegistroAuditoriaRepositoryCustom {
    Page<RegistroAuditoria> buscar(String entidade, String usuario, String acao,
                                   Instant inicio, Instant fim, Pageable pageable);
}
