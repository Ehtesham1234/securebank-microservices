package com.ehtesham.ai_service.feign;

import com.ehtesham.ai_service.dto.LoanSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "loan-service",
        fallback = LoanServiceClientFallback.class)
public interface LoanServiceClient {

    @GetMapping("/api/v1/loans/my")
    List<LoanSummary> getMyLoans(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size);
}
