package com.ehtesham.securebank.websocket.service;

import com.ehtesham.securebank.common.enums.TransactionType;
import com.ehtesham.securebank.websocket.dto.BalanceUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class WebSocketNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    WebSocketNotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(
            SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendBalanceUpdate(
            Long userId,
            String accountNumber,
            BigDecimal newBalance,
            BigDecimal transactionAmount,
            TransactionType transactionType,
            String transactionRef,
            String description) {

        BalanceUpdateMessage message = BalanceUpdateMessage.builder()
                .accountNumber(accountNumber)
                .newBalance(newBalance)
                .transactionAmount(transactionAmount)
                .transactionType(transactionType)
                .transactionRef(transactionRef)
                .description(description)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = "/topic/balance/" + userId;

        messagingTemplate.convertAndSend(destination, message);

        log.info("WebSocket balance update sent to userId={}, " +
                        "account={}, newBalance={}",
                userId, accountNumber, newBalance);
    }
}