CREATE TABLE password_reset_otps (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     email VARCHAR(255) NOT NULL,
                                     otp VARCHAR(6) NOT NULL,
                                     expiry_date DATETIME NOT NULL,
                                     used BOOLEAN NOT NULL DEFAULT FALSE,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);