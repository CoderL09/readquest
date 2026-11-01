CREATE TABLE reading_events (
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 chapter_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 current_paragraph_index INT NOT NULL, completed_paragraphs INT NOT NULL, total_paragraphs INT NOT NULL,
 reading_duration_ms BIGINT NOT NULL, created_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(user_id,event_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;