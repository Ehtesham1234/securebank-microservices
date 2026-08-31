package com.ehtesham.loan_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserSearchClientFallback implements UserSearchClient {

    private static final Logger log =
            LoggerFactory.getLogger(UserSearchClientFallback.class);

    @Override
    public List<Long> searchUserIds(String q) {
        log.warn("Circuit breaker: securebank-api unavailable for name " +
                "search on q='{}' — degrading to no name-based matches " +
                "(id search is unaffected).", q);
        return List.of();
    }
}