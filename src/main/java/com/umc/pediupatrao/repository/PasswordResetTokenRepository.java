package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.PasswordResetToken;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PasswordResetTokenRepository extends
        MongoRepository<PasswordResetToken, String>, PasswordResetTokenAtomicOperations {
    Optional<PasswordResetToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Date now);
    void deleteByUserId(String userId);
}
