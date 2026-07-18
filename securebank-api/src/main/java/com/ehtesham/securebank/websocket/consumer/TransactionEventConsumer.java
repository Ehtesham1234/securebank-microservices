package com.ehtesham.securebank.websocket.consumer;

import com.ehtesham.securebank.common.enums.TransactionType;
import com.ehtesham.securebank.websocket.service.WebSocketNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final WebSocketNotificationService wsService;
    private final ObjectMapper objectMapper;

    public TransactionEventConsumer(
            WebSocketNotificationService wsService,
            ObjectMapper objectMapper) {
        this.wsService = wsService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "transaction-events",
            groupId = "securebank-api-websocket-group")
    public void handleTransactionEvent(
            @Payload String payload,
            @Header(KafkaHeaders.OFFSET) long offset) {

        try {
            JsonNode node = objectMapper.readTree(payload);

            Long userId = node.get("userId").asLong();
            String accountNumber =
                    node.get("accountNumber").asText();
            BigDecimal newBalance =
                    new BigDecimal(node.get("newBalance").asText());
            BigDecimal amount =
                    new BigDecimal(node.get("amount").asText());
            String type = node.get("transactionType").asText();
            String ref = node.get("transactionRef").asText();
            String description =
                    node.has("description")
                            ? node.get("description").asText()
                            : "";

            log.info("WebSocket push: userId={}, type={}, " +
                            "amount={}, offset={}",
                    userId, type, amount, offset);

            wsService.sendBalanceUpdate(
                    userId,
                    accountNumber,
                    newBalance,
                    amount,
                    TransactionType.valueOf(type),
                    ref,
                    description);

        } catch (Exception e) {
            log.error("Failed to process transaction event: {}",
                    e.getMessage());
        }
    }
}