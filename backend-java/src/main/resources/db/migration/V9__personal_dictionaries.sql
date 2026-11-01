CREATE TABLE user_dictionaries (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 display_name VARCHAR(120) NOT NULL, original_file_name VARCHAR(255) NOT NULL,
 storage_key VARCHAR(100) NOT NULL, byte_size BIGINT NOT NULL, checksum_sha256 CHAR(64) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
