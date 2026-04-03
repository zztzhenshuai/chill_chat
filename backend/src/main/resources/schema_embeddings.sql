CREATE TABLE IF NOT EXISTS message_embeddings (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id  BIGINT NOT NULL UNIQUE,
    user_id     BIGINT NOT NULL,
    embedding   MEDIUMTEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, create_time)
);
