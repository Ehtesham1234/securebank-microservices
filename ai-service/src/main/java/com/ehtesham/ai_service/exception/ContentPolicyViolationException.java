package com.ehtesham.ai_service.exception;

/**
 * Thrown by {@link com.ehtesham.ai_service.advisors.SafetyGuardrailAdvisor}
 * when a request is blocked before it ever reaches the model (suspected
 * prompt injection, jailbreak attempt, etc).
 *
 * This is deliberately a plain unchecked exception rather than trying to
 * hand-construct a synthetic ChatClientResponse inside the advisor: the
 * advisor's job is to detect and reject, not to know how the caller (a
 * blocking controller vs. an SSE stream) wants the rejection presented.
 * BankingAiService catches this specifically and turns it into the right
 * shape for each entry point (ChatResponse for /chat, a Flux element for
 * /chat/stream).
 */
public class ContentPolicyViolationException extends RuntimeException {

    public ContentPolicyViolationException(String message) {
        super(message);
    }
}
