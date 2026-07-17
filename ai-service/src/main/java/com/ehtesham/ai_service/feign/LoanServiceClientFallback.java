package com.ehtesham.ai_service.feign;

import com.ehtesham.ai_service.dto.LoanSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoanServiceClientFallback implements LoanServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(LoanServiceClientFallback.class);

    @Override
    public List<LoanSummary> getMyLoans(Long userId, int page, int size) {
        log.warn("Circuit breaker: loan-service unavailable for userId={}", userId);
        return List.of();
    }
}
