package com.ehtesham.ai_service.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * H3 fix: every account-service/loan-service endpoint actually responds
 * with {"success":..,"status":..,"message":..,"data":<payload>,"timestamp":..}
 * — not the bare payload. The Feign clients here were declared with plain
 * return types (e.g. List&lt;X&gt;), which can never deserialize
 * successfully against that shape; every call was silently hitting the
 * circuit breaker fallback instead. Wrap the declared return type in this
 * and unwrap with getData().
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiEnvelope<T> {

    private T data;

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
