CREATE TABLE ai_analysis_cache (
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_json LONGTEXT NOT NULL, expires_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(user_id,content_hash), FOREIGN KEY(user_id) REFERENCES users(id), INDEX analysis_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;