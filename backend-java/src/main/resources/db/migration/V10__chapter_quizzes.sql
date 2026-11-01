CREATE TABLE chapter_quizzes (
 chapter_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_hash CHAR(64) NOT NULL, questions_json LONGTEXT NULL,
 status VARCHAR(16) NOT NULL, request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_expires_at TIMESTAMP(3) NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
