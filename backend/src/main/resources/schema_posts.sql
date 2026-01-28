-- Posts Table
CREATE TABLE IF NOT EXISTS posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT,
    image_url TEXT,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
);

-- Comments Table
CREATE TABLE IF NOT EXISTS comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_post_id (post_id)
);

-- Likes Table (to prevent duplicate likes)
CREATE TABLE IF NOT EXISTS post_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    UNIQUE KEY uk_post_user (post_id, user_id)
);
