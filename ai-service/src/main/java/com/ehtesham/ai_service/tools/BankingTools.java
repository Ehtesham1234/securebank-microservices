package com.ehtesham.ai_service.tools;

import com.ehtesham.ai_service.dto.AccountSummary;
import com.ehtesham.ai_service.dto.LoanSummary;
import com.ehtesham.ai_service.dto.TransactionSummary;
import com.ehtesham.ai_service.feign.AccountServiceClient;
import com.ehtesham.ai_service.feign.LoanServiceClient;
import com.ehtesham.ai_service.feign.TransactionServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class BankingTools {

    private static final Logger log = LoggerFactory.getLogger(BankingTools.class);
    public static final String USER_ID_CONTEXT_KEY = "userId";

    private final AccountServiceClient accountClient;
    private final LoanServiceClient loanClient;
    private final TransactionServiceClient transactionClient;

    public BankingTools(
            AccountServiceClient accountClient,
            LoanServiceClient loanClient,
            TransactionServiceClient transactionClient) {
        this.accountClient = accountClient;
        this.loanClient = loanClient;
        this.transactionClient = transactionClient;
    }

    private Long userId(ToolContext toolContext) {
        Object value = toolContext.getContext().get(USER_ID_CONTEXT_KEY);
        if (!(value instanceof Long userId)) {
            // Fails loudly rather than falling back to some default —
            // a tool call with no verified user id must never proceed.
            throw new IllegalStateException(
                    "Tool invoked with no verified userId in ToolContext. " +
                            "This indicates a wiring bug in BankingAiService, " +
                            "not a client-facing condition.");
        }
        return userId;
    }

    @Tool(
            name = "get_user_accounts",
            description = """
            Get all bank accounts belonging to the current user.
            Returns account number, type (SAVINGS/CURRENT/FIXED_DEPOSIT),
            status (ACTIVE/FROZEN/CLOSED), and current balance.
            Call this when the user asks about their accounts, balance,
            available funds, or account status.
            """
    )
    public List<AccountSummary> getUserAccounts(ToolContext toolContext) {
        Long userId = userId(toolContext);
        log.info("Tool: get_user_accounts(userId={})", userId);
        List<AccountSummary> accounts = accountClient.getMyAccounts().getData();
        log.info("Tool: get_user_accounts returned {} accounts", accounts.size());
        return accounts;
    }

    @Tool(
            name = "get_user_loans",
            description = """
            Get all loans belonging to the current user.
            Returns loan reference, type (PERSONAL/HOME/CAR),
            status (PENDING/ACTIVE/CLOSED/DEFAULTED), principal amount,
            outstanding balance, monthly EMI amount, number of EMIs
            paid, EMIs remaining, and next EMI due date.
            Call this when the user asks about loans, EMI, debt,
            outstanding balance, or loan repayment schedule.
            """
    )
    public List<LoanSummary> getUserLoans(ToolContext toolContext) {
        Long userId = userId(toolContext);
        log.info("Tool: get_user_loans(userId={})", userId);
        List<LoanSummary> loans = loanClient.getMyLoans(0, 10).getData().getContent();
        log.info("Tool: get_user_loans returned {} loans", loans.size());
        return loans;
    }

    @Tool(
            name = "get_transaction_history",
            description = """
            Get recent transaction history for the user's account.
            Requires the account ID (obtained from get_user_accounts).
            Returns transaction reference, type (DEPOSIT/WITHDRAW/
            TRANSFER_IN/TRANSFER_OUT), amount, balance after transaction,
            status, description, and timestamp.
            Call this when the user asks about spending, recent
            transactions, payment history, or wants to know what
            happened to their money.
            """
    )
    public List<TransactionSummary> getTransactionHistory(
            @ToolParam(description = "Account ID, obtained from get_user_accounts") Long accountId,
            ToolContext toolContext) {
        Long userId = userId(toolContext);
        log.info("Tool: get_transaction_history(accountId={}, userId={})", accountId, userId);
        List<TransactionSummary> txns =
                transactionClient.getTransactionHistory(accountId, 0, 20)
                        .getData().getContent();
        log.info("Tool: get_transaction_history returned {} transactions", txns.size());
        return txns;
    }

    @Tool(
            name = "calculate_spending_analysis",
            description = """
            Calculate a detailed spending analysis for the user's account.
            Requires the account ID (obtained from get_user_accounts).
            Returns:
            - Total amount spent (WITHDRAW + TRANSFER_OUT transactions)
            - Total amount received (DEPOSIT + TRANSFER_IN transactions)
            - Net cash flow (received minus spent)
            - Count of each transaction type
            All amounts use BigDecimal for financial precision.
            Call this when the user asks 'how much did I spend',
            'what is my cash flow', or 'how much money came in'.
            """
    )
    public SpendingAnalysis calculateSpendingAnalysis(
            @ToolParam(description = "Account ID, obtained from get_user_accounts") Long accountId,
            ToolContext toolContext) {

        Long userId = userId(toolContext);
        log.info("Tool: calculate_spending_analysis(accountId={}, userId={})", accountId, userId);

        List<TransactionSummary> txns =
                transactionClient.getTransactionHistory(accountId, 0, 100)
                        .getData().getContent();

        BigDecimal totalSpent = txns.stream()
                .filter(t -> "WITHDRAW".equals(t.getType()) || "TRANSFER_OUT".equals(t.getType()))
                .map(TransactionSummary::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalReceived = txns.stream()
                .filter(t -> "DEPOSIT".equals(t.getType()) || "TRANSFER_IN".equals(t.getType()))
                .map(TransactionSummary::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netFlow = totalReceived.subtract(totalSpent).setScale(2, RoundingMode.HALF_UP);

        long withdrawCount = txns.stream().filter(t -> "WITHDRAW".equals(t.getType())).count();
        long transferOutCount = txns.stream().filter(t -> "TRANSFER_OUT".equals(t.getType())).count();
        long depositCount = txns.stream().filter(t -> "DEPOSIT".equals(t.getType())).count();
        long transferInCount = txns.stream().filter(t -> "TRANSFER_IN".equals(t.getType())).count();

        return SpendingAnalysis.builder()
                .totalSpent(totalSpent)
                .totalReceived(totalReceived)
                .netCashFlow(netFlow)
                .withdrawCount(withdrawCount)
                .transferOutCount(transferOutCount)
                .depositCount(depositCount)
                .transferInCount(transferInCount)
                .transactionsAnalysed(txns.size())
                .build();
    }

    @lombok.Getter
    @lombok.Setter
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SpendingAnalysis {
        private BigDecimal totalSpent;
        private BigDecimal totalReceived;
        private BigDecimal netCashFlow;
        private long withdrawCount;
        private long transferOutCount;
        private long depositCount;
        private long transferInCount;
        private int transactionsAnalysed;
    }
}