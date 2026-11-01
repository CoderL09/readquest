CREATE TABLE book_embeddings (
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 cache_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 vector_json MEDIUMTEXT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(book_id,cache_key),FOREIGN KEY(book_id) REFERENCES books(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
