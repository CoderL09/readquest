CREATE TABLE remote_import_jobs (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_url TEXT NOT NULL,url_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 display_url VARCHAR(2048) NOT NULL,title VARCHAR(300) NOT NULL DEFAULT '',kind VARCHAR(32) NOT NULL DEFAULT 'web-article',
 status VARCHAR(16) NOT NULL DEFAULT 'queued',book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 file_job_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,run_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 attempts INT NOT NULL DEFAULT 0,lease_expires_at TIMESTAMP(3) NULL,error_code VARCHAR(64) NULL,
 active_owner VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 source_metadata_json LONGTEXT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(book_id) REFERENCES books(id),
 UNIQUE KEY remote_url_owner(user_id,url_hash),INDEX remote_job_work(status,lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
