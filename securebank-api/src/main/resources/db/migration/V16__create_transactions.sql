CREATE TABLE transactions (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              transaction_ref VARCHAR(255) NOT NULL UNIQUE,
                              account_id BIGINT NOT NULL,
                              type VARCHAR(50) NOT NULL,
                              amount DECIMAL(19,4) NOT NULL,
                              balance_after DECIMAL(19,4) NOT NULL,
                              status VARCHAR(50) NOT NULL,
                              description VARCHAR(255),
                              related_account_id BIGINT,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

                              CONSTRAINT fk_transactions_account
                                  FOREIGN KEY (account_id) REFERENCES accounts(id),

                              CONSTRAINT fk_transactions_related_account
                                  FOREIGN KEY (related_account_id) REFERENCES accounts(id)
);
