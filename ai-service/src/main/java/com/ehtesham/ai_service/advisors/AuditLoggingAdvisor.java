package com.ehtesham.ai_service.advisors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Logs every AI interaction for compliance/audit trail purposes.
 *
 * Deliberately the OUTERMOST advisor (lowest order value = highest
 * precedence = wraps everything else, including SafetyGuardrailAdvisor).
 * That's the opposite of where it sat in the original version, and on
 * purpose: if it ran *inside* the guardrail, a blocked prompt-injection
 * attempt would throw before audit logging ever executed, leaving no
 * record of the attempt. Wrapping it outermost with try/finally means
 * every request is logged — allowed, blocked, or erroring out downstream.
 */
@Component
public class AuditLoggingAdvisor implements CallAdvisor, StreamAdvisor, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(AuditLoggingAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        logRequest(request);

        try {
            ChatClientResponse response = chain.nextCall(request);
            logResponse(request, System.currentTimeMillis() - start, null);
            return response;
        } catch (RuntimeException ex) {
            logResponse(request, System.currentTimeMillis() - start, ex);
            throw ex;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        long start = System.currentTimeMillis();
        logRequest(request);

        return chain.nextStream(request)
                .doOnComplete(() -> logResponse(request, System.currentTimeMillis() - start, null))
                .doOnError(ex -> logResponse(request, System.currentTimeMillis() - start, ex));
    }

    private void logRequest(ChatClientRequest request) {
        String userText = request.prompt().getUserMessage().getText();
        Object conversationId = request.context().get(ChatMemory.CONVERSATION_ID);
        log.info("AI request: question_length={}, conversation_id={}",
                userText == null ? 0 : userText.length(), conversationId);
    }

    private void logResponse(ChatClientRequest request, long durationMs, Throwable error) {
        if (error != null) {
            log.info("AI response: duration_ms={}, outcome=REJECTED_OR_FAILED, reason={}",
                    durationMs, error.getMessage());
        } else {
            log.info("AI response: duration_ms={}, outcome=OK", durationMs);
        }
    }

    @Override
    public String getName() {
        return "AuditLoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
