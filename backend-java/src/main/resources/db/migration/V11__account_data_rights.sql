CREATE TABLE account_deletion_jobs (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 status VARCHAR(16) NOT NULL, avatar_name VARCHAR(255) NULL,dictionary_key VARCHAR(100) NULL,
 last_error VARCHAR(64) NULL,attempts INT NOT NULL DEFAULT 0,
 requested_at TIMESTAMP(3) NOT NULL, completed_at TIMESTAMP(3) NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),INDEX deletion_pending(status,requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
