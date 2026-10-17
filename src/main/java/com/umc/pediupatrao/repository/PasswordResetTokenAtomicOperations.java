package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.PasswordResetToken;
import java.util.Date;

public interface PasswordResetTokenAtomicOperations {
    PasswordResetToken consumeValidToken(String tokenHash, Date now);
}
