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
    public ApiEnvelope<PageContent<LoanSummary>> getMyLoans(int page, int size) {
        log.warn("Circuit breaker: loan-service unavailable for getMyLoans");
        PageContent<LoanSummary> emptyPage = new PageContent<>();
        emptyPage.setContent(List.of());
        ApiEnvelope<PageContent<LoanSummary>> empty = new ApiEnvelope<>();
        empty.setData(emptyPage);
        return empty;
    }
}
