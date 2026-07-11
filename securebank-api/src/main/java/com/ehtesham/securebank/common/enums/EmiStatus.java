package com.ehtesham.securebank.common.enums;

public enum EmiStatus {
    PENDING,    // due but not yet paid
    PAID,       // paid on time or before due date
    OVERDUE,    // past due date, not paid
    WAIVED      // admin waived this EMI (rare, edge case)
}
