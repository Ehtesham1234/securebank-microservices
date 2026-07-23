package com.ehtesham.account_service.account.consumer;


import com.ehtesham.account_service.account.dto.LoanApprovedEvent;
import com.ehtesham.account_service.account.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class LoanEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(LoanEventConsumer.class);

    private final AccountService accountService;

    public LoanEventConsumer(AccountService accountService) {
        this.accountService = accountService;
    }

    @KafkaListener(
            topics = "loan-events",
            groupId = "account-service-group",
            containerFactory = "loanEventListenerContainerFactory")
    public void handleLoanApproved(
            @Payload LoanApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received LoanApprovedEvent: loanId={}, " +
                        "accountId={}, amount={}, offset={}",
                event.getLoanId(), event.getAccountId(),
                event.getAmount(), offset);

        accountService.processCreditForLoan(
                event.getLoanId(),
                event.getAccountId(),
                event.getAmount(),
                event.getLoanRef());
    }
}