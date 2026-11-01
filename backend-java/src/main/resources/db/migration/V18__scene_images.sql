CREATE TABLE scene_images (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_name VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 retention_until TIMESTAMP(3) NOT NULL,created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(book_id) REFERENCES books(id),INDEX scene_expiry(retention_until),INDEX scene_owner(user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
