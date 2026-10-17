package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.PasswordResetToken;
import java.util.Date;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class PasswordResetTokenAtomicOperationsImpl implements PasswordResetTokenAtomicOperations {
    private final MongoTemplate mongoTemplate;

    public PasswordResetTokenAtomicOperationsImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public PasswordResetToken consumeValidToken(String tokenHash, Date now) {
        Query query = Query.query(Criteria.where("_id").is(tokenHash).and("expiresAt").gt(now));
        return mongoTemplate.findAndRemove(query, PasswordResetToken.class);
    }
}
