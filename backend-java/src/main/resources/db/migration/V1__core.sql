CREATE TABLE users (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE, display_name VARCHAR(50) NOT NULL, avatar_url VARCHAR(2000) NOT NULL DEFAULT '',
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(16) NOT NULL DEFAULT 'user', status VARCHAR(32) NOT NULL DEFAULT 'active',
 created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE refresh_sessions (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE, expires_at TIMESTAMP(3) NOT NULL, revoked_at TIMESTAMP(3) NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), INDEX sessions_expiry(expires_at), INDEX sessions_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE books (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, owner_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 title VARCHAR(1000) NOT NULL, author VARCHAR(500) NOT NULL DEFAULT '', status VARCHAR(32) NOT NULL,
 asset_scope VARCHAR(32) NOT NULL DEFAULT 'legacy-quarantined', visibility VARCHAR(16) NOT NULL DEFAULT 'public', moderation_status VARCHAR(16) NOT NULL DEFAULT 'unreviewed',
 word_count BIGINT NOT NULL DEFAULT 0, total_chapters INT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), INDEX books_owner_created(owner_id,created_at), INDEX books_catalog(visibility,status,moderation_status,asset_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE chapters (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 chapter_index INT NOT NULL, stage_title VARCHAR(1000) NOT NULL DEFAULT '', content LONGTEXT NOT NULL, content_text LONGTEXT NULL, stage_summary TEXT NULL,
 FOREIGN KEY(book_id) REFERENCES books(id), UNIQUE KEY chapter_order(book_id,chapter_index), UNIQUE KEY chapter_book(id,book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE book_notes (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, chapter_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 paragraph_index INT NULL, quote TEXT NOT NULL, note TEXT NOT NULL, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id),
 INDEX notes_user_created(user_id,created_at,id), CHECK(paragraph_index IS NULL OR paragraph_index>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE library_entries (
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 shelf_state VARCHAR(16) NOT NULL DEFAULT 'saved', created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(user_id,book_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id),
 INDEX library_user_updated(user_id,updated_at), CHECK(shelf_state IN ('saved','reading','completed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE reading_progress (
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 chapter_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, current_paragraph_index INT NOT NULL DEFAULT 0, completed_paragraphs INT NOT NULL DEFAULT 0,
 total_paragraphs INT NOT NULL DEFAULT 0, total_reading_ms BIGINT NOT NULL DEFAULT 0, completed_at TIMESTAMP(3) NULL, updated_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(user_id,book_id,chapter_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id),
 CHECK(current_paragraph_index>=0 AND completed_paragraphs>=0 AND total_paragraphs>=completed_paragraphs AND total_reading_ms>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
