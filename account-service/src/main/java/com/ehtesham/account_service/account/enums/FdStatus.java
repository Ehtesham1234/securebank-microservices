package com.ehtesham.account_service.account.enums;

// C9 fix: previously there was no status on a Fixed Deposit at all —
// nothing anywhere ever paid one out at maturity. ACTIVE is the only
// state a scheduled job looks for; once processMaturedFixedDeposits()
// pays it out, it flips to MATURED so it's never picked up again.
public enum FdStatus {
    ACTIVE,
    MATURED
}
