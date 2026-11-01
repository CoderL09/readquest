CREATE TABLE review_cards (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, lookup_item_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, chapter_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 source_sentence_key VARCHAR(512) NOT NULL, concept_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL, kind VARCHAR(32) NOT NULL, original_sentence TEXT NOT NULL,
 target_json LONGTEXT NULL, concept_json LONGTEXT NULL, fsrs_json LONGTEXT NOT NULL, due_at TIMESTAMP(3) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'active', version BIGINT NOT NULL DEFAULT 0,
 algorithm_version VARCHAR(64) NOT NULL DEFAULT 'ts-fsrs-5.4.1/FSRS-6',created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(lookup_item_id) REFERENCES lookup_items(id) ON DELETE CASCADE,
 FOREIGN KEY(book_id) REFERENCES books(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id),
 UNIQUE KEY concept_occurrence(user_id,book_id,concept_fingerprint),INDEX reviews_due(user_id,status,due_at), CHECK(kind IN ('wordMeaning','sentenceConcept')),
 CHECK((kind='wordMeaning' AND target_json IS NOT NULL AND concept_json IS NULL) OR (kind='sentenceConcept' AND concept_json IS NOT NULL AND target_json IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE review_sessions (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 active_owner VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE, group_size INT NOT NULL,current_index INT NOT NULL DEFAULT 0,total INT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'active',current_stage VARCHAR(32) NOT NULL DEFAULT 'initialAnswer',current_attempt_id VARCHAR(36) NULL,
 created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,FOREIGN KEY(user_id) REFERENCES users(id),CHECK(group_size IN (5,10,20))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE review_session_cards (
 session_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,position INT NOT NULL,card_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 PRIMARY KEY(session_id,position),FOREIGN KEY(session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,FOREIGN KEY(card_id) REFERENCES review_cards(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE review_attempts (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 session_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,card_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 occurrence_index INT NOT NULL,answer_kind VARCHAR(32) NOT NULL,stage VARCHAR(32) NOT NULL,user_answer TEXT NOT NULL,response_time_ms BIGINT NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,grading_state VARCHAR(16) NOT NULL,grade_json LONGTEXT NULL,
 failure_code VARCHAR(64) NULL,rating_applied BOOLEAN NOT NULL DEFAULT FALSE,derived_rating VARCHAR(16) NULL,next_stage VARCHAR(32) NOT NULL,
 reference_json LONGTEXT NOT NULL,card_version BIGINT NOT NULL,lease_expires_at TIMESTAMP(3) NOT NULL,created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,FOREIGN KEY(card_id) REFERENCES review_cards(id) ON DELETE SET NULL,
 UNIQUE KEY review_idempotency(user_id,idempotency_key),INDEX review_occurrence(user_id,session_id,occurrence_index,answer_kind,stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE review_progressions (
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 session_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,PRIMARY KEY(user_id,event_id),
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(session_id) REFERENCES review_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;