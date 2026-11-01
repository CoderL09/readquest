ALTER TABLE refresh_sessions ADD COLUMN created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
CREATE TABLE user_entitlements (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, source_order_no VARCHAR(64) NOT NULL UNIQUE,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, code VARCHAR(64) NOT NULL, label VARCHAR(80) NOT NULL,
 starts_at TIMESTAMP(3) NOT NULL, ends_at TIMESTAMP(3) NULL, FOREIGN KEY(user_id) REFERENCES users(id),
 UNIQUE KEY entitlement_start(user_id,code,starts_at), INDEX entitlement_active(user_id,code,ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE membership_redemption_codes (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 code_suffix CHAR(4) NOT NULL, label VARCHAR(80) NOT NULL, duration_days INT NULL, expires_at TIMESTAMP(3) NOT NULL,
 redeemed_at TIMESTAMP(3) NULL, redeemed_by_user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, revoked_at TIMESTAMP(3) NULL,
 FOREIGN KEY(redeemed_by_user_id) REFERENCES users(id), CHECK(duration_days IS NULL OR duration_days BETWEEN 1 AND 3650)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE feature_usage (
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 feature VARCHAR(64) NOT NULL, occurred_at TIMESTAMP(3) NOT NULL, result VARCHAR(32) NOT NULL, units INT NOT NULL,
 PRIMARY KEY(user_id,request_id), FOREIGN KEY(user_id) REFERENCES users(id), INDEX usage_day(user_id,occurred_at), CHECK(units>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE rate_limit_buckets (
 bucket_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, window_start BIGINT NOT NULL, hits INT NOT NULL,
 expires_at TIMESTAMP(3) NOT NULL, INDEX rate_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;