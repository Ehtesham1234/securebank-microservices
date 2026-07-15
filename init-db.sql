-- This runs automatically when MySQL container first starts
-- Creates all three databases

CREATE DATABASE IF NOT EXISTS securebank
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS securebank_accounts
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS securebank_loans
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant root access to all databases
GRANT ALL PRIVILEGES ON securebank.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON securebank_accounts.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON securebank_loans.* TO 'root'@'%';
FLUSH PRIVILEGES;