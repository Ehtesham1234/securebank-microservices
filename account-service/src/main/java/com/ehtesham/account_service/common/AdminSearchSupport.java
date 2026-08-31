package com.ehtesham.account_service.common;

import com.ehtesham.account_service.client.UserSearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared by every admin listing endpoint in this service (accounts,
 * transactions, cards) that needs to support "search by user name/email"
 * without a real database join — Account/Transaction/Card only store a
 * userId, and User lives in securebank-api's own database.
 */
@Component
@RequiredArgsConstructor
public class AdminSearchSupport {

    /** A sentinel id that can never match a real row — used in place of
     *  an empty IN (...) list, which some JPQL/Hibernate versions choke
     *  on when the collection is genuinely empty. */
    public static final List<Long> NO_MATCH = List.of(-1L);

    private final UserSearchClient userSearchClient;

    public Long parseNumericId(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(search.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Only calls out to securebank-api when the term ISN'T already
     *  numeric — a numeric term is handled entirely locally (as an id
     *  or userId match), so there's nothing to resolve remotely. */
    public List<Long> resolveUserIds(String search, Long numericId) {
        if (search == null || search.isBlank() || numericId != null) {
            return NO_MATCH;
        }
        List<Long> ids = userSearchClient.searchUserIds(search.trim());
        return ids.isEmpty() ? NO_MATCH : ids;
    }
}