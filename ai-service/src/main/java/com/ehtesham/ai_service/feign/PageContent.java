package com.ehtesham.ai_service.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * H3 fix: account-service/loan-service return Spring Data Page&lt;T&gt;
 * for list endpoints, which serializes with a "content" array plus a
 * bunch of pagination metadata (totalElements, pageable, sort, etc.) that
 * this service has no use for. ignoreUnknown=true so that metadata
 * doesn't break deserialization here, and so it keeps working if
 * account-service/loan-service upgrade Spring Boot versions that change
 * exactly which pagination fields get serialized.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageContent<T> {

    private List<T> content;

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }
}
