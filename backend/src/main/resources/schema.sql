-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    avatar VARCHAR(255),
    signature VARCHAR(255),
    gender INT DEFAULT 0 COMMENT '0: Unknown, 1: Male, 2: Female',
    birthday DATE,
    location VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Messages Table
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    is_group BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender_target (sender_id, target_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert dummy users if not exists
INSERT IGNORE INTO users (id, username, password, avatar) VALUES 
(1001, 'Alice', '123456', 'https://api.dicebear.com/7.x/avataaars/svg?seed=1001'),
(1002, 'Bob', '123456', 'https://api.dicebear.com/7.x/avataaars/svg?seed=1002');
