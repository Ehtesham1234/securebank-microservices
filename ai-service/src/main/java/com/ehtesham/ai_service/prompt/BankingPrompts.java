package com.ehtesham.ai_service.prompt;

/**
 * Centralised prompt templates.
 * Keeping prompts out of service/tool classes makes them
 * easy to iterate without changing business logic.
 */
public final class BankingPrompts {

    private BankingPrompts() {}

    public static final String USER_CONTEXT_TEMPLATE =
            "%s\n\n[Verified user ID: %d. Use this ID in all " +
                    "tool calls. Do not ask the user for their ID.]";

    public static final String FINANCIAL_SUMMARY_PROMPT = """
            Provide a complete financial summary for the current user.
            Call ALL of the following tools in order:
            1. get_user_accounts — to get all accounts and balances
            2. get_user_loans — to get all loans and EMI information
            3. get_transaction_history — for the primary savings account
            4. calculate_spending_analysis — for spending breakdown
            Then synthesise all results into a clear, structured summary.
            """;
}
