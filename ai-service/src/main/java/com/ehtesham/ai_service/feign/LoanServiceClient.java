package com.ehtesham.ai_service.feign;

import com.ehtesham.ai_service.dto.LoanSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "loan-service",
        configuration = IdentityForwardingFeignConfig.class,
        fallback = LoanServiceClientFallback.class)
public interface LoanServiceClient {

    // H3 fix: real response is ApiResponse<Page<LoanResponse>>, not a
    // bare list.
    @GetMapping("/api/v1/loans/my")
    ApiEnvelope<PageContent<LoanSummary>> getMyLoans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size);
}
