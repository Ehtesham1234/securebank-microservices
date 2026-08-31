package com.ehtesham.account_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Resolves a name/email search term (from admin account/transaction/card
 * search) into matching user ids on securebank-api, since Account,
 * Transaction, and Card all live in this service's own database with no
 * relationship to the User table — only reached when the search term
 * isn't already numeric, see AdminSearchSupport.
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