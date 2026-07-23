package com.ehtesham.account_service.transaction.publisher;

import com.ehtesham.account_service.transaction.enums.TransactionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes transaction completion events to Kafka.
 * securebank-api consumes these events and pushes to WebSocket.
 * This decouples account-service from the WebSocket infrastructure.
 */
@Component
public class TransactionEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionEventPublisher.class);

    private static final String TOPIC = "transaction-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TransactionEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishTransactionCompleted(
            Long userId,
            String accountNumber,
            BigDecimal newBalance,
            BigDecimal amount,
            TransactionType transactionType,
            String transactionRef,
            String description) {

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("userId", userId);
            event.put("accountNumber", accountNumber);
            event.put("newBalance", newBalance.toPlainString());
            event.put("amount", amount.toPlainString());
            event.put("transactionType", transactionType.name());
            event.put("transactionRef", transactionRef);
            event.put("description",
                    description != null ? description : "");

            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC, userId.toString(), payload);

            log.info("Published transaction event: type={}, " +
                            "userId={}, ref={}",
                    transactionType, userId, transactionRef);

        } catch (JsonProcessingException e) {
            // Log but don't fail the transaction
            // WebSocket push is best-effort, not critical
            log.error("Failed to publish transaction event: {}",
                    e.getMessage());
        }
    }
}