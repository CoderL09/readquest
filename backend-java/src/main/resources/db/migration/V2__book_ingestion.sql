ALTER TABLE books ADD COLUMN file_path VARCHAR(1000) NULL;
ALTER TABLE books ADD COLUMN file_hash CHAR(64) NULL;
ALTER TABLE books ADD COLUMN cover_url VARCHAR(2000) NOT NULL DEFAULT '';
ALTER TABLE books ADD COLUMN reading_mode VARCHAR(16) NOT NULL DEFAULT 'continuous';
ALTER TABLE books ADD COLUMN progress INT NOT NULL DEFAULT 0;
ALTER TABLE books ADD COLUMN error_message VARCHAR(1000) NULL;
ALTER TABLE books ADD COLUMN rights_attested_at TIMESTAMP(3) NULL;
CREATE UNIQUE INDEX books_owner_file ON books(owner_id,file_hash);
ALTER TABLE chapters ADD COLUMN content_html LONGTEXT NULL;
ALTER TABLE chapters ADD COLUMN source_href VARCHAR(1000) NULL;
CREATE TABLE book_import_jobs (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'queued',
 attempts INT NOT NULL DEFAULT 0, run_id VARCHAR(36) NULL, lease_expires_at TIMESTAMP(3) NULL,
 error_message VARCHAR(1000) NULL, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id),
 INDEX imports_pending(status,lease_expires_at,created_at), INDEX imports_owner(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
