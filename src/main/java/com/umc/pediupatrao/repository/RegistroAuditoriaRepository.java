package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RegistroAuditoriaRepository extends MongoRepository<RegistroAuditoria, String> {
}
