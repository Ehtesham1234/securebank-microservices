package com.ehtesham.ai_service.service;

import com.ehtesham.ai_service.dto.ChatResponse;
import com.ehtesham.ai_service.dto.FinancialSummary;
import com.ehtesham.ai_service.exception.ContentPolicyViolationException;
import com.ehtesham.ai_service.prompt.BankingPrompts;
import com.ehtesham.ai_service.security.SecurityUtils;
import com.ehtesham.ai_service.tools.BankingTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.Map;
@Service
public class BankingAiService {

    private static final Logger log = LoggerFactory.getLogger(BankingAiService.class);

    private static final String BLOCKED_MESSAGE =
            "I'm sorry, I can only help with banking and financial questions. " +
                    "Please ask me about your accounts, loans, or transactions.";

    private final ChatClient chatClient;
    private final BankingTools bankingTools;
    private final SecurityUtils securityUtils;
    public BankingAiService(ChatClient chatClient, BankingTools bankingTools, SecurityUtils securityUtils) {
        this.chatClient = chatClient;
        this.bankingTools = bankingTools;
        this.securityUtils = securityUtils;
    }

    public ChatResponse chat(String question, String conversationId) {

        Long userId =  securityUtils.getCurrentUserId();
        String convId = resolveConversationId(conversationId, userId);

        log.info("Chat request: userId={}, convId={}, question_length={}",
                userId, convId, question.length());

        try {
            String enrichedQuestion = String.format(
                    BankingPrompts.USER_CONTEXT_TEMPLATE, question, userId);

            String answer = chatClient.prompt()
                    .user(enrichedQuestion)
                    .tools(bankingTools)
                    .toolContext(Map.of(BankingTools.USER_ID_CONTEXT_KEY, userId))
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

    public Flux<String> chatStream(String question, String conversationId) {

        Long userId =  securityUtils.getCurrentUserId();
        String convId = resolveConversationId(conversationId, userId);

        String enrichedQuestion = String.format(
                BankingPrompts.USER_CONTEXT_TEMPLATE, question, userId);

        return chatClient.prompt()
                .user(enrichedQuestion)
                .tools(bankingTools)
                .toolContext(Map.of(BankingTools.USER_ID_CONTEXT_KEY, userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .stream()
                .content()
                .onErrorResume(ContentPolicyViolationException.class,
                        e -> Flux.just(BLOCKED_MESSAGE))
                .onErrorResume(Exception.class,
                        e -> Flux.just("I apologize, an error occurred while " +
                                "processing your request. Please try again."));
    }

    public FinancialSummary getFinancialSummary() {

        Long userId = securityUtils.getCurrentUserId();
        String convId = "user-" + userId;

        log.info("Financial summary requested: userId={}", userId);

        String prompt = String.format(
                BankingPrompts.USER_CONTEXT_TEMPLATE,
                BankingPrompts.FINANCIAL_SUMMARY_PROMPT, userId);

        return chatClient.prompt()
                .user(prompt)
                .tools(bankingTools)
                .toolContext(Map.of(BankingTools.USER_ID_CONTEXT_KEY, userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .call()
                .entity(FinancialSummary.class);
    }

    private String resolveConversationId(String conversationId, Long userId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "user-" + userId;
        }
        return "user-" + userId + ":" + conversationId;
    }
}