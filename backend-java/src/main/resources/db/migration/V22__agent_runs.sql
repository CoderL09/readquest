CREATE TABLE agent_runs (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_key VARCHAR(128) NOT NULL,
 input_hash VARCHAR(64) NOT NULL,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(24) NOT NULL,
 group_size INT NOT NULL,
 result_json LONGTEXT NULL,
 events_json LONGTEXT NOT NULL,
 session_json LONGTEXT NULL,
 error_code VARCHAR(64) NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 CONSTRAINT agent_run_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT agent_run_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
 CONSTRAINT agent_request_once UNIQUE(user_id,request_key),
 INDEX agent_stale_runs(state,created_at)
);
