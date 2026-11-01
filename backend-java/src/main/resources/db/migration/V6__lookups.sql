CREATE TABLE lookup_items (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 chapter_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, paragraph_id VARCHAR(128) NULL, paragraph_index INT NULL,
 sentence_id VARCHAR(128) NULL, chunk_id VARCHAR(128) NULL, source_sentence_key VARCHAR(512) NOT NULL,
 occurrence_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL, type VARCHAR(32) NOT NULL, text VARCHAR(2500) NOT NULL, normalized_text VARCHAR(2500) NOT NULL,
 target_json LONGTEXT NULL, original_sentence TEXT NOT NULL, explanation TEXT NOT NULL, translation TEXT NOT NULL,
 grammar_analysis TEXT NOT NULL, reading_strategy TEXT NOT NULL, dictionary_json LONGTEXT NULL,
 review_status VARCHAR(16) NOT NULL DEFAULT 'new', mastery_level INT NOT NULL DEFAULT 0,
 lookup_time TIMESTAMP(3) NOT NULL, next_review_at TIMESTAMP(3) NOT NULL, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id),
 UNIQUE KEY word_occurrence(user_id,occurrence_key), INDEX lookups_user_time(user_id,lookup_time,id),
 INDEX lookups_chapter(user_id,book_id,chapter_id,paragraph_index), INDEX lookups_due(user_id,next_review_at,review_status),
 CHECK(type IN ('word','phrase','sentence','grammar','sentencePattern')), CHECK(mastery_level BETWEEN 0 AND 6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;