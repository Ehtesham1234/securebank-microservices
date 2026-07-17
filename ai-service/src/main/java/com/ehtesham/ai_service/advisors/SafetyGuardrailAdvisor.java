package com.ehtesham.ai_service.advisors;

import com.ehtesham.ai_service.exception.ContentPolicyViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Blocks obvious prompt-injection / jailbreak attempts before the prompt
 * ever reaches the model.
 *
 * Implements BOTH CallAdvisor and StreamAdvisor. The original version only
 * implemented the non-streaming interface, which meant /api/v1/ai/chat/stream
 * silently bypassed the guardrail entirely — the streaming endpoint would
 * have been the easy way around it.
 *
 * Spring AI also ships a built-in SafeGuardAdvisor for blocking a static
 * word list; this stays custom because prompt-injection phrase patterns
 * are a different (and banking-specific) concern from a sensitive-word
 * filter, and because we want a specific exception type the service layer
 * can catch and translate per entry point.
 */
@Component
public class SafetyGuardrailAdvisor implements CallAdvisor, StreamAdvisor, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(SafetyGuardrailAdvisor.class);

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore your system prompt",
            "you are now",
            "act as if you are",
            "forget your rules",
            "bypass",
            "jailbreak",
            "reveal your prompt",
            "what is your system prompt"
    );

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        guard(request);
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // Do the check eagerly (before subscription) rather than inside a
        // map/flatMap — a blocked request should never touch the model,
        // even during onSubscribe / connection setup.
        guard(request);
        return chain.nextStream(request);
    }

    private void guard(ChatClientRequest request) {
        String userText = request.prompt().getUserMessage().getText();
        String lower = userText == null ? "" : userText.toLowerCase();

        boolean blocked = BLOCKED_PATTERNS.stream().anyMatch(lower::contains);

        if (blocked) {
            log.warn("Blocked potential prompt injection (first 100 chars): {}",
                    userText.substring(0, Math.min(100, userText.length())));

            throw new ContentPolicyViolationException(
                    "I'm sorry, I can only help with banking and financial " +
                            "questions. Please ask me about your accounts, loans, " +
                            "or transactions.");
        }
    }

    @Override
    public String getName() {
        return "SafetyGuardrailAdvisor";
    }

    @Override
    public int getOrder() {
        // Runs just inside AuditLoggingAdvisor so a blocked attempt is
        // still audited (see AuditLoggingAdvisor for why it sits outside).
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
