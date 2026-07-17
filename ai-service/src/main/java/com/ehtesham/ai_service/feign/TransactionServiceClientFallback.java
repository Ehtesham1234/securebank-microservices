package com.ehtesham.ai_service.feign;

import com.ehtesham.ai_service.dto.TransactionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionServiceClientFallback implements TransactionServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionServiceClientFallback.class);

    @Override
    public List<TransactionSummary> getTransactionHistory(
            Long accountId, Long userId, int page, int size) {
        log.warn("Circuit breaker: securebank-api unavailable for accountId={}", accountId);
        return List.of();
    }
}
