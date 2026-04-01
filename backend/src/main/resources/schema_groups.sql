-- Groups Table (named chat_groups to avoid keyword issues in some DBs, though groups is usually reserved)
CREATE TABLE IF NOT EXISTS chat_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    owner_id BIGINT NOT NULL,
    avatar VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Group Members Table
CREATE TABLE IF NOT EXISTS group_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('owner', 'admin', 'member') NOT NULL DEFAULT 'member',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, user_id),
    INDEX idx_user_id (user_id)
);

-- Add role column to existing group_members table if upgrading
-- Using continue-on-error: if column already exists MySQL raises 1060 (ignored by Spring)
ALTER TABLE group_members ADD COLUMN role ENUM('owner', 'admin', 'member') NOT NULL DEFAULT 'member';
