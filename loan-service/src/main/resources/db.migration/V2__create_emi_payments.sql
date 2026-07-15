CREATE TABLE emi_payments (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              loan_id BIGINT NOT NULL,
                              emi_number INT NOT NULL,
                              emi_amount DECIMAL(19,4) NOT NULL,
                              interest_component DECIMAL(19,4) NOT NULL,
                              principal_component DECIMAL(19,4) NOT NULL,
                              outstanding_after DECIMAL(19,4) NOT NULL,
                              due_date DATE NOT NULL,
                              paid_date DATE,
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              transaction_ref VARCHAR(100),
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_emi_loan
                                  FOREIGN KEY (loan_id) REFERENCES loans(id)
);