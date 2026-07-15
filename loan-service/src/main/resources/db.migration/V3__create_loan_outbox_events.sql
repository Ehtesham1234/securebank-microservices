CREATE TABLE loan_outbox_events (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    topic VARCHAR(100) NOT NULL,
                                    aggregate_id VARCHAR(255) NOT NULL,
                                    event_type VARCHAR(50) NOT NULL,
                                    payload TEXT NOT NULL,
                                    published BOOLEAN NOT NULL DEFAULT FALSE,
                                    published_at TIMESTAMP NULL,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    INDEX idx_published (published),
                                    INDEX idx_created_at (created_at)
);