package com.ehtesham.loan_service.consumer;


import com.ehtesham.loan_service.dto.AccountCreditedEvent;
import com.ehtesham.loan_service.service.LoanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class LoanSagaConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(LoanSagaConsumer.class);

    private final LoanService loanService;

    public LoanSagaConsumer(LoanService loanService) {
        this.loanService = loanService;
    }

    @KafkaListener(
            topics = "account-events",
            groupId = "loan-saga-group",
            containerFactory = "accountEventListenerContainerFactory")
    public void handleAccountEvent(
            @Payload AccountCreditedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Loan Saga received account event: " +
                        "loanId={}, success={}, offset={}",
                event.getLoanId(), event.isSuccess(), offset);

        if (event.isSuccess()) {
            loanService.activateLoan(
                    event.getLoanId(),
                    event.getTransactionRef());
        } else {
            loanService.failLoan(
                    event.getLoanId(),
                    event.getFailureReason());
        }
    }
}