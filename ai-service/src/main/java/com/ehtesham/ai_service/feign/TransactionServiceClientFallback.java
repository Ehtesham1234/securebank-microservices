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
    public ApiEnvelope<PageContent<TransactionSummary>> getTransactionHistory(
            Long accountId, int page, int size) {
        log.warn("Circuit breaker: account-service unavailable for accountId={}", accountId);
        PageContent<TransactionSummary> emptyPage = new PageContent<>();
        emptyPage.setContent(List.of());
        ApiEnvelope<PageContent<TransactionSummary>> empty = new ApiEnvelope<>();
        empty.setData(emptyPage);
        return empty;
    }
}
