package com.ehtesham.ai_service.service;

import com.ehtesham.ai_service.dto.ChatResponse;
import com.ehtesham.ai_service.dto.FinancialSummary;
import com.ehtesham.ai_service.exception.ContentPolicyViolationException;
import com.ehtesham.ai_service.prompt.BankingPrompts;
import com.ehtesham.ai_service.security.SecurityContextHolder;
import com.ehtesham.ai_service.tools.BankingTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class BankingAiService {

    private static final Logger log = LoggerFactory.getLogger(BankingAiService.class);

    private static final String BLOCKED_MESSAGE =
            "I'm sorry, I can only help with banking and financial questions. " +
                    "Please ask me about your accounts, loans, or transactions.";

    private final ChatClient chatClient;
    private final BankingTools bankingTools;

    public BankingAiService(ChatClient chatClient, BankingTools bankingTools) {
        this.chatClient = chatClient;
        this.bankingTools = bankingTools;
    }

    /**
     * Standard chat — returns the complete response after the model finishes.
     * userId is sourced from SecurityContextHolder (verified gateway
     * headers), never from the request body or model output.
     *
     * FIX: previously re-instantiated MessageChatMemoryAdvisor here even
     * though the ChatClient bean already registers one as a default
     * advisor — every call was injecting conversation history into the
     * prompt TWICE. Cross-cutting advisors (memory, safety, audit,
     * logging) now live only in AiConfig; this method just sets the
     * per-request conversation id parameter.
     */
    public ChatResponse chat(String question, String conversationId) {

        Long userId = SecurityContextHolder.get().getUserId();
        String convId = resolveConversationId(conversationId, userId);

        log.info("Chat request: userId={}, convId={}, question_length={}",
                userId, convId, question.length());

        try {
            String enrichedQuestion = String.format(
                    BankingPrompts.USER_CONTEXT_TEMPLATE, question, userId);

            String answer = chatClient.prompt()
                    .user(enrichedQuestion)
                    .tools(bankingTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                    .call()
                    .content();

            return ChatResponse.builder()
                    .answer(answer)
                    .conversationId(convId)
                    .userId(userId)
                    .build();

        } catch (ContentPolicyViolationException e) {
            return ChatResponse.builder()
                    .answer(BLOCKED_MESSAGE)
                    .conversationId(convId)
                    .userId(userId)
                    .build();

        } catch (Exception e) {
            log.error("AI chat error: userId={}, error={}", userId, e.getMessage());
            return ChatResponse.builder()
                    .answer("I'm sorry, I'm unable to process your request right " +
                            "now. Please try again in a moment.")
                    .conversationId(convId)
                    .userId(userId)
                    .build();
        }
    }

    /**
     * Streaming chat — tokens are returned as they're generated.
     *
     * FIX: the guardrail advisor now implements StreamAdvisor too (see
     * SafetyGuardrailAdvisor), so a blocked prompt-injection attempt is
     * actually caught here instead of silently bypassing the safety
     * check the way it did when the advisor only implemented the
     * non-streaming interface.
     */
    public Flux<String> chatStream(String question, String conversationId) {

        Long userId = SecurityContextHolder.get().getUserId();
        String convId = resolveConversationId(conversationId, userId);

        String enrichedQuestion = String.format(
                BankingPrompts.USER_CONTEXT_TEMPLATE, question, userId);

        return chatClient.prompt()
                .user(enrichedQuestion)
                .tools(bankingTools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .stream()
                .content()
                .onErrorResume(ContentPolicyViolationException.class,
                        e -> Flux.just(BLOCKED_MESSAGE))
                .onErrorResume(Exception.class,
                        e -> Flux.just("I apologize, an error occurred while " +
                                "processing your request. Please try again."));
    }

    /**
     * Structured output — returns FinancialSummary as a typed POJO.
     *
     * FIX: this call previously omitted ChatMemory.CONVERSATION_ID
     * entirely. Since the ChatClient now always carries a
     * MessageChatMemoryAdvisor by default, an unset conversation id
     * would fall back to a shared/default bucket — meaning every user's
     * financial-summary request could read and pollute the SAME memory
     * entry. Always scope it per user, same as chat()/chatStream().
     */
    public FinancialSummary getFinancialSummary() {

        Long userId = SecurityContextHolder.get().getUserId();
        String convId = "user-" + userId;

        log.info("Financial summary requested: userId={}", userId);

        String prompt = String.format(
                BankingPrompts.USER_CONTEXT_TEMPLATE,
                BankingPrompts.FINANCIAL_SUMMARY_PROMPT, userId);

        return chatClient.prompt()
                .user(prompt)
                .tools(bankingTools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .call()
                .entity(FinancialSummary.class);
    }

    private String resolveConversationId(String conversationId, Long userId) {
        return (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : "user-" + userId;
    }
}
