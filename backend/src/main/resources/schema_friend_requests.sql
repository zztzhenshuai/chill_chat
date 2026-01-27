-- Friend Requests Table
CREATE TABLE IF NOT EXISTS friend_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED
    reason VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_receiver (receiver_id),
    INDEX idx_requester (requester_id)
);
