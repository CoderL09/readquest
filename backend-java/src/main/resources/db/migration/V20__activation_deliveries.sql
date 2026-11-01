ALTER TABLE membership_redemption_codes ADD COLUMN bound_user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
CREATE TABLE activation_purchases (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 email VARCHAR(254) NOT NULL,email_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) NOT NULL,code_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 delivery_status VARCHAR(16) NOT NULL,delivery_mode VARCHAR(16) NOT NULL,
 sealed_code TEXT NULL,lease_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,lease_until TIMESTAMP(3) NULL,
 attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMP(3) NULL,
 code_expires_at TIMESTAMP(3) NULL,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE KEY activation_submission(user_id,request_key),INDEX activation_owner(user_id,created_at),INDEX activation_delivery(delivery_status,next_attempt_at),
 INDEX activation_email_limit(email_hash,created_at),FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
