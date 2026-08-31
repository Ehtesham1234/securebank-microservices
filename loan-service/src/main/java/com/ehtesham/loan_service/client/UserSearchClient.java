package com.ehtesham.loan_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Resolves a name/email search term (from admin loan search) into
 * matching user ids on securebank-api — Loan lives in this service's own
 * database with no relationship to the User table. Only reached when the
 * search term isn't already numeric.
 */
@FeignClient(
        name = "securebank-api",
        contextId = "userSearchServiceClient",
        configuration = IdentityForwardingFeignConfig.class,
        fallback = UserSearchClientFallback.class)
public interface UserSearchClient {

    @GetMapping("/api/v1/internal/users/search-ids")
    List<Long> searchUserIds(@RequestParam("q") String q);
}