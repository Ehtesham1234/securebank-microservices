package com.ehtesham.loan_service.common;

import com.ehtesham.loan_service.client.UserSearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminSearchSupport {

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

    public List<Long> resolveUserIds(String search, Long numericId) {
        if (search == null || search.isBlank() || numericId != null) {
            return NO_MATCH;
        }
        List<Long> ids = userSearchClient.searchUserIds(search.trim());
        return ids.isEmpty() ? NO_MATCH : ids;
    }
}