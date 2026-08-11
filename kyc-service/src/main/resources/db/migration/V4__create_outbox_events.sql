-- Bug fix: kyc-service previously had no outbox table at all — KYC
-- notification events (submitted/verified/rejected) were published via
-- a bare @Async fire-and-forget kafkaTemplate.send() with nothing
-- persisted, so a failed send or a JVM restart mid-flight lost the
-- notification outright with zero trace and no retry. Mirrors the
-- outbox_events table account-service and loan-service already have.
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_published_created (published, created_at)
);
