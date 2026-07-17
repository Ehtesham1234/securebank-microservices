package com.ehtesham.ai_service.config;

import com.ehtesham.ai_service.advisors.AuditLoggingAdvisor;
import com.ehtesham.ai_service.advisors.SafetyGuardrailAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * In-memory chat memory with a sliding window of 20 messages.
     * In production, swap this for a JDBC- or Redis-backed
     * ChatMemoryRepository so history survives a restart — add
     * spring-ai-starter-memory-jdbc and it auto-configures against
     * your existing DataSource.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    /**
     * ChatClient wired with ALL cross-cutting advisors registered ONCE,
     * at build time, as the framework docs recommend. The previous
     * version registered MessageChatMemoryAdvisor here AND re-instantiated
     * a second MessageChatMemoryAdvisor per request in BankingAiService —
     * that meant conversation history was being injected into the prompt
     * twice on every call (wasted tokens, and a subtly wrong prompt shape).
     * The fix: register every advisor exactly once here; callers only set
     * the ChatMemory.CONVERSATION_ID parameter per request (see
     * BankingAiService).
     *
     * Order (outermost to innermost):
     *   1. AuditLoggingAdvisor      — always logs, even blocked requests
     *   2. SafetyGuardrailAdvisor   — blocks prompt-injection attempts
     *   3. MessageChatMemoryAdvisor — injects conversation history
     *   4. SimpleLoggerAdvisor      — DEBUG-level request/response dump
     */
    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            AuditLoggingAdvisor auditLoggingAdvisor,
            SafetyGuardrailAdvisor safetyGuardrailAdvisor) {

        return builder
                .defaultSystem("""
                    You are a professional banking assistant for SecureBank.
                    You help customers understand their accounts, loans,
                    transactions, and financial position.

                    STRICT RULES:
                    - ALWAYS call the appropriate tool to fetch REAL data
                      before answering any financial question.
                      Never invent balances, amounts, or dates.
                    - Format all amounts with the ₹ symbol and 2 decimal
                      places (e.g. ₹45,230.00).
                    - If a tool returns an empty list, tell the customer
                      politely that no data was found for that category.
                    - Never ask the customer for their account number,
                      password, or personal details. You have secure
                      access to their data via tools.
                    - Decline questions unrelated to banking and finance.
                    - Be concise, professional, and accurate.
                    """)
                .defaultAdvisors(
                        auditLoggingAdvisor,
                        safetyGuardrailAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor())
                .build();
    }
}
