package com.ehtesham.ai_service.controller;

import com.ehtesham.ai_service.dto.ChatRequest;
import com.ehtesham.ai_service.dto.ChatResponse;
import com.ehtesham.ai_service.dto.FinancialSummary;
import com.ehtesham.ai_service.service.BankingAiService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final BankingAiService aiService;

    public AiController(BankingAiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Standard chat — complete response returned at once.
     * userId sourced from X-User-Id header (set by API gateway).
     *
     * FIX: added @RateLimiter — AI calls have a real per-token cost, so
     * this endpoint is worth protecting from retry storms / accidental
     * hammering at the service level (tune per-user limits at the
     * gateway; this is a coarse backstop). Config lives in
     * application.properties under resilience4j.ratelimiter.instances.ai-chat.
     */
    @RateLimiter(name = "ai-chat")
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(
                aiService.chat(request.getQuestion(), request.getConversationId()));
    }

    /**
     * Streaming chat — tokens streamed as Server-Sent Events.
     *
     * Usage with fetch:
     * const response = await fetch('/api/v1/ai/chat/stream', {
     *   method: 'POST',
     *   headers: {'Content-Type': 'application/json',
     *             'Authorization': 'Bearer <token>'},
     *   body: JSON.stringify({question: 'What is my balance?'})
     * });
     * const reader = response.body.getReader();
     */
    @RateLimiter(name = "ai-chat")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        return aiService.chatStream(request.getQuestion(), request.getConversationId());
    }

    /**
     * Structured financial summary. Returns a typed FinancialSummary POJO.
     */
    @GetMapping("/summary")
    public ResponseEntity<FinancialSummary> financialSummary() {
        return ResponseEntity.ok(aiService.getFinancialSummary());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Service running");
    }
}
