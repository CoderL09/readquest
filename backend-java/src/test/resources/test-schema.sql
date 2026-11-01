CREATE TABLE users (
 id VARCHAR(36) PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE, display_name VARCHAR(50) NOT NULL, avatar_url VARCHAR(2000) NOT NULL DEFAULT '',
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(16) NOT NULL DEFAULT 'user', status VARCHAR(32) NOT NULL DEFAULT 'active',
 created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL
);
CREATE TABLE refresh_sessions (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL,
 token_hash CHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMP(3) NOT NULL, revoked_at TIMESTAMP(3) NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), INDEX sessions_expiry(expires_at), INDEX sessions_user(user_id)
);
CREATE TABLE books (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NULL,
 title VARCHAR(1000) NOT NULL, author VARCHAR(500) NOT NULL DEFAULT '', status VARCHAR(32) NOT NULL,
 asset_scope VARCHAR(32) NOT NULL DEFAULT 'legacy-quarantined', visibility VARCHAR(16) NOT NULL DEFAULT 'public', moderation_status VARCHAR(16) NOT NULL DEFAULT 'unreviewed',
 word_count BIGINT NOT NULL DEFAULT 0, total_chapters INT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), INDEX books_owner_created(owner_id,created_at), INDEX books_catalog(visibility,status,moderation_status,asset_scope)
);
CREATE TABLE chapters (
 id VARCHAR(36) PRIMARY KEY, book_id VARCHAR(36) NOT NULL,
 chapter_index INT NOT NULL, stage_title VARCHAR(1000) NOT NULL DEFAULT '', content LONGTEXT NOT NULL, content_text LONGTEXT NULL, stage_summary TEXT NULL,
 FOREIGN KEY(book_id) REFERENCES books(id), UNIQUE KEY chapter_order(book_id,chapter_index), UNIQUE KEY chapter_book(id,book_id)
);
CREATE TABLE book_notes (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL,
 book_id VARCHAR(36) NOT NULL, chapter_id VARCHAR(36) NULL,
 paragraph_index INT NULL, quote TEXT NOT NULL, note TEXT NOT NULL, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id),
 INDEX notes_user_created(user_id,created_at,id), CHECK(paragraph_index IS NULL OR paragraph_index>=0)
);
CREATE TABLE library_entries (
 user_id VARCHAR(36) NOT NULL, book_id VARCHAR(36) NOT NULL,
 shelf_state VARCHAR(16) NOT NULL DEFAULT 'saved', created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(user_id,book_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id),
 INDEX library_user_updated(user_id,updated_at), CHECK(shelf_state IN ('saved','reading','completed'))
);
CREATE TABLE reading_progress (
 user_id VARCHAR(36) NOT NULL, book_id VARCHAR(36) NOT NULL,
 chapter_id VARCHAR(36) NOT NULL, current_paragraph_index INT NOT NULL DEFAULT 0, completed_paragraphs INT NOT NULL DEFAULT 0,
 total_paragraphs INT NOT NULL DEFAULT 0, total_reading_ms BIGINT NOT NULL DEFAULT 0, completed_at TIMESTAMP(3) NULL, updated_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(user_id,book_id,chapter_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id),
 CHECK(current_paragraph_index>=0 AND completed_paragraphs>=0 AND total_paragraphs>=completed_paragraphs AND total_reading_ms>=0)
);

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
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 book_id VARCHAR(36) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'queued',
 phase VARCHAR(24) NOT NULL DEFAULT 'queued', progress INT NOT NULL DEFAULT 0,
 attempts INT NOT NULL DEFAULT 0, run_id VARCHAR(36) NULL, lease_expires_at TIMESTAMP(3) NULL,
 error_message VARCHAR(1000) NULL, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id),
 INDEX imports_pending(status,lease_expires_at,created_at), INDEX imports_owner(user_id,created_at)
);

CREATE TABLE reading_events (
 user_id VARCHAR(36) NOT NULL,
 event_id VARCHAR(36) NOT NULL,
 book_id VARCHAR(36) NOT NULL,
 chapter_id VARCHAR(36) NOT NULL,
 current_paragraph_index INT NOT NULL, completed_paragraphs INT NOT NULL, total_paragraphs INT NOT NULL,
 reading_duration_ms BIGINT NOT NULL, created_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(user_id,event_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id)
);
ALTER TABLE refresh_sessions ADD COLUMN created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
CREATE TABLE user_entitlements (
 id VARCHAR(36) PRIMARY KEY, source_order_no VARCHAR(64) NOT NULL UNIQUE,
 user_id VARCHAR(36) NOT NULL, code VARCHAR(64) NOT NULL, label VARCHAR(80) NOT NULL,
 starts_at TIMESTAMP(3) NOT NULL, ends_at TIMESTAMP(3) NULL, FOREIGN KEY(user_id) REFERENCES users(id),
 UNIQUE KEY entitlement_start(user_id,code,starts_at), INDEX entitlement_active(user_id,code,ends_at)
);
CREATE TABLE membership_redemption_codes (
 id VARCHAR(36) PRIMARY KEY, code_hash CHAR(64) NOT NULL UNIQUE,
 code_suffix CHAR(4) NOT NULL, label VARCHAR(80) NOT NULL, duration_days INT NULL, expires_at TIMESTAMP(3) NOT NULL,
 redeemed_at TIMESTAMP(3) NULL, redeemed_by_user_id VARCHAR(36) NULL, revoked_at TIMESTAMP(3) NULL,
 FOREIGN KEY(redeemed_by_user_id) REFERENCES users(id), CHECK(duration_days IS NULL OR duration_days BETWEEN 1 AND 3650)
);
CREATE TABLE feature_usage (
 user_id VARCHAR(36) NOT NULL, request_id VARCHAR(128) NOT NULL,
 feature VARCHAR(64) NOT NULL, occurred_at TIMESTAMP(3) NOT NULL, result VARCHAR(32) NOT NULL, units INT NOT NULL,
 PRIMARY KEY(user_id,request_id), FOREIGN KEY(user_id) REFERENCES users(id), INDEX usage_day(user_id,occurred_at), CHECK(units>=0)
);
CREATE TABLE rate_limit_buckets (
 bucket_key CHAR(64) PRIMARY KEY, window_start BIGINT NOT NULL, hits INT NOT NULL,
 expires_at TIMESTAMP(3) NOT NULL, INDEX rate_expiry(expires_at)
);
CREATE TABLE ai_analysis_cache (
 user_id VARCHAR(36) NOT NULL, content_hash CHAR(64) NOT NULL,
 result_json LONGTEXT NOT NULL, expires_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(user_id,content_hash), FOREIGN KEY(user_id) REFERENCES users(id), INDEX analysis_expiry(expires_at)
);
CREATE TABLE lookup_items (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL, book_id VARCHAR(36) NOT NULL,
 chapter_id VARCHAR(36) NULL, paragraph_id VARCHAR(128) NULL, paragraph_index INT NULL,
 sentence_id VARCHAR(128) NULL, chunk_id VARCHAR(128) NULL, source_sentence_key VARCHAR(512) NOT NULL,
 occurrence_key CHAR(64) NULL, type VARCHAR(32) NOT NULL, text VARCHAR(2500) NOT NULL, normalized_text VARCHAR(2500) NOT NULL,
 target_json LONGTEXT NULL, original_sentence TEXT NOT NULL, explanation TEXT NOT NULL, translation TEXT NOT NULL,
 grammar_analysis TEXT NOT NULL, reading_strategy TEXT NOT NULL, dictionary_json LONGTEXT NULL,
 review_status VARCHAR(16) NOT NULL DEFAULT 'new', mastery_level INT NOT NULL DEFAULT 0,
 lookup_time TIMESTAMP(3) NOT NULL, next_review_at TIMESTAMP(3) NOT NULL, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id),
 UNIQUE KEY word_occurrence(user_id,occurrence_key), INDEX lookups_user_time(user_id,lookup_time,id),
 INDEX lookups_chapter(user_id,book_id,chapter_id,paragraph_index), INDEX lookups_due(user_id,next_review_at,review_status),
 CHECK(type IN ('word','phrase','sentence','grammar','sentencePattern')), CHECK(mastery_level BETWEEN 0 AND 6)
);
CREATE TABLE review_cards (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL, lookup_item_id VARCHAR(36) NULL UNIQUE,
 book_id VARCHAR(36) NOT NULL, chapter_id VARCHAR(36) NULL,
 source_sentence_key VARCHAR(512) NOT NULL, concept_fingerprint CHAR(64) NULL, kind VARCHAR(32) NOT NULL, original_sentence TEXT NOT NULL,
 target_json LONGTEXT NULL, concept_json LONGTEXT NULL, fsrs_json LONGTEXT NOT NULL, due_at TIMESTAMP(3) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'active', version BIGINT NOT NULL DEFAULT 0,
 algorithm_version VARCHAR(64) NOT NULL DEFAULT 'ts-fsrs-5.4.1/FSRS-6',created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(lookup_item_id) REFERENCES lookup_items(id) ON DELETE CASCADE,
 FOREIGN KEY(book_id) REFERENCES books(id), FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id),
 UNIQUE KEY concept_occurrence(user_id,book_id,concept_fingerprint),INDEX reviews_due(user_id,status,due_at), CHECK(kind IN ('wordMeaning','sentenceConcept')),
 CHECK((kind='wordMeaning' AND target_json IS NOT NULL AND concept_json IS NULL) OR (kind='sentenceConcept' AND concept_json IS NOT NULL AND target_json IS NULL))
);
CREATE TABLE review_sessions (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL,
 active_owner VARCHAR(36) NULL UNIQUE, group_size INT NOT NULL,current_index INT NOT NULL DEFAULT 0,total INT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'active',current_stage VARCHAR(32) NOT NULL DEFAULT 'initialAnswer',current_attempt_id VARCHAR(36) NULL,
 created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,FOREIGN KEY(user_id) REFERENCES users(id),CHECK(group_size IN (5,10,20))
);
CREATE TABLE review_session_cards (
 session_id VARCHAR(36) NOT NULL,position INT NOT NULL,card_id VARCHAR(36) NULL,
 PRIMARY KEY(session_id,position),FOREIGN KEY(session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,FOREIGN KEY(card_id) REFERENCES review_cards(id) ON DELETE SET NULL
);
CREATE TABLE review_attempts (
 id VARCHAR(36) PRIMARY KEY,user_id VARCHAR(36) NOT NULL,
 session_id VARCHAR(36) NOT NULL,card_id VARCHAR(36) NULL,
 occurrence_index INT NOT NULL,answer_kind VARCHAR(32) NOT NULL,stage VARCHAR(32) NOT NULL,user_answer TEXT NOT NULL,response_time_ms BIGINT NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL,grading_state VARCHAR(16) NOT NULL,grade_json LONGTEXT NULL,
 failure_code VARCHAR(64) NULL,rating_applied BOOLEAN NOT NULL DEFAULT FALSE,derived_rating VARCHAR(16) NULL,next_stage VARCHAR(32) NOT NULL,
 reference_json LONGTEXT NOT NULL,card_version BIGINT NOT NULL,lease_expires_at TIMESTAMP(3) NOT NULL,created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,FOREIGN KEY(card_id) REFERENCES review_cards(id) ON DELETE SET NULL,
 UNIQUE KEY review_idempotency(user_id,idempotency_key),INDEX review_occurrence(user_id,session_id,occurrence_index,answer_kind,stage)
);
CREATE TABLE review_progressions (
 user_id VARCHAR(36) NOT NULL,event_id VARCHAR(36) NOT NULL,
 session_id VARCHAR(36) NOT NULL,PRIMARY KEY(user_id,event_id),
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(session_id) REFERENCES review_sessions(id) ON DELETE CASCADE
);
ALTER TABLE review_attempts ADD COLUMN semantic_verdict VARCHAR(16) NULL;
ALTER TABLE review_attempts ADD COLUMN semantic_score DOUBLE NULL;
CREATE TABLE reading_profiles (
 user_id VARCHAR(36) PRIMARY KEY,
 current_lexile INT NOT NULL DEFAULT 500,comfort_zone_min INT NOT NULL DEFAULT 400,comfort_zone_max INT NOT NULL DEFAULT 550,
 i_plus_one_min INT NOT NULL DEFAULT 550,i_plus_one_max INT NOT NULL DEFAULT 650,total_reading_minutes DOUBLE NOT NULL DEFAULT 0,
 average_lookup_per_sentence DOUBLE NOT NULL DEFAULT 0,average_comprehension_score DOUBLE NOT NULL DEFAULT 0,reading_confidence DOUBLE NOT NULL DEFAULT 0.5,
 last_adjustment_reason VARCHAR(1000) NOT NULL DEFAULT '',last_adjusted_at TIMESTAMP(3) NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE book_metadata (
 id VARCHAR(36) PRIMARY KEY,title VARCHAR(1000) NOT NULL,author VARCHAR(500) NOT NULL,cover VARCHAR(2000) NOT NULL DEFAULT '',description TEXT NOT NULL,
 lexile INT NOT NULL,difficulty_level VARCHAR(16) NOT NULL,genre VARCHAR(120) NOT NULL,source_url VARCHAR(2000) NOT NULL,
 download_status VARCHAR(32) NOT NULL DEFAULT 'metadataOnly',linked_book_id VARCHAR(36) NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),FOREIGN KEY(linked_book_id) REFERENCES books(id),INDEX metadata_lexile(lexile)
);
CREATE TABLE calibration_sessions (
 id VARCHAR(36) PRIMARY KEY,user_id VARCHAR(36) NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'active',
 current_step INT NOT NULL,estimated_lexile INT NOT NULL,final_profile_json LONGTEXT NULL,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),INDEX calibration_user(user_id,created_at)
);
CREATE TABLE calibration_samples (
 session_id VARCHAR(36) NOT NULL,step INT NOT NULL,sample_id VARCHAR(32) NOT NULL,lexile INT NOT NULL,text TEXT NOT NULL,
 started_at TIMESTAMP(3) NOT NULL,finished_at TIMESTAMP(3) NULL,reading_seconds INT NULL,lookup_count INT NULL,comprehension_score DOUBLE NULL,flow_score DOUBLE NULL,
 PRIMARY KEY(session_id,step),UNIQUE KEY calibration_sample(session_id,sample_id),FOREIGN KEY(session_id) REFERENCES calibration_sessions(id) ON DELETE CASCADE
);
CREATE TABLE user_dictionaries (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL UNIQUE,
 display_name VARCHAR(120) NOT NULL, original_file_name VARCHAR(255) NOT NULL,
 storage_key VARCHAR(100) NOT NULL, byte_size BIGINT NOT NULL, checksum_sha256 CHAR(64) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TABLE chapter_quizzes (
 chapter_id VARCHAR(36) PRIMARY KEY,
 book_id VARCHAR(36) NOT NULL,
 source_hash CHAR(64) NOT NULL, questions_json LONGTEXT NULL,
 status VARCHAR(16) NOT NULL, request_id VARCHAR(36) NULL,
 lease_expires_at TIMESTAMP(3) NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(chapter_id,book_id) REFERENCES chapters(id,book_id)
);

CREATE TABLE account_deletion_jobs (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL UNIQUE,
 status VARCHAR(16) NOT NULL, avatar_name VARCHAR(255) NULL,dictionary_key VARCHAR(100) NULL,
 last_error VARCHAR(64) NULL,attempts INT NOT NULL DEFAULT 0,
 requested_at TIMESTAMP(3) NOT NULL, completed_at TIMESTAMP(3) NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),INDEX deletion_pending(status,requested_at)
);

ALTER TABLE books ADD COLUMN metadata_json LONGTEXT NULL;

ALTER TABLE refresh_sessions ADD COLUMN family_id VARCHAR(36) NOT NULL DEFAULT '';
UPDATE refresh_sessions SET family_id=id WHERE family_id='';
CREATE INDEX session_family ON refresh_sessions(user_id,family_id,revoked_at);

CREATE TABLE remote_import_jobs (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 request_url TEXT NOT NULL,url_hash CHAR(64) NOT NULL,
 display_url VARCHAR(2048) NOT NULL,title VARCHAR(300) NOT NULL DEFAULT '',kind VARCHAR(32) NOT NULL DEFAULT 'web-article',
 status VARCHAR(16) NOT NULL DEFAULT 'queued',book_id VARCHAR(36) NULL,
 file_job_id VARCHAR(36) NULL,run_id VARCHAR(36) NULL,
 attempts INT NOT NULL DEFAULT 0,lease_expires_at TIMESTAMP(3) NULL,error_code VARCHAR(64) NULL,
 active_owner VARCHAR(36) NULL UNIQUE,
 created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 source_metadata_json LONGTEXT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(book_id) REFERENCES books(id),
 UNIQUE KEY remote_url_owner(user_id,url_hash),INDEX remote_job_work(status,lease_expires_at)
);

CREATE TABLE catalog_snapshots (
 cache_key VARCHAR(64) PRIMARY KEY,
 items_json LONGTEXT NOT NULL, fetched_at TIMESTAMP(3) NOT NULL,expires_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE group_memberships (
 id VARCHAR(36) PRIMARY KEY,
 book_id VARCHAR(36) NOT NULL,user_id VARCHAR(36) NOT NULL,
 role VARCHAR(16) NOT NULL,muted_until TIMESTAMP(3) NULL,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE KEY group_member(book_id,user_id),INDEX joined_groups(user_id,created_at),FOREIGN KEY(book_id) REFERENCES books(id),FOREIGN KEY(user_id) REFERENCES users(id),CHECK(role IN ('member','moderator','admin'))
);
CREATE TABLE group_messages (
 sequence BIGINT AUTO_INCREMENT PRIMARY KEY,id VARCHAR(36) NOT NULL UNIQUE,
 book_id VARCHAR(36) NOT NULL,sender_user_id VARCHAR(64) NOT NULL,
 body TEXT NOT NULL,client_message_id VARCHAR(128) NOT NULL,
 deleted_at TIMESTAMP(3) NULL,deleted_by_user_id VARCHAR(64) NOT NULL DEFAULT '',deletion_reason VARCHAR(500) NOT NULL DEFAULT '',created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE KEY group_message_submission(book_id,sender_user_id,client_message_id),INDEX group_message_cursor(book_id,sequence),FOREIGN KEY(book_id) REFERENCES books(id)
);
CREATE TABLE user_blocks (
 blocker_user_id VARCHAR(36) NOT NULL,blocked_user_id VARCHAR(36) NOT NULL,created_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(blocker_user_id,blocked_user_id),FOREIGN KEY(blocker_user_id) REFERENCES users(id),FOREIGN KEY(blocked_user_id) REFERENCES users(id)
);
CREATE TABLE content_reports (
 id VARCHAR(36) PRIMARY KEY,book_id VARCHAR(36) NOT NULL,
 reporter_user_id VARCHAR(36) NOT NULL,target_type VARCHAR(16) NOT NULL,
 target_message_id VARCHAR(36) NULL,target_user_id VARCHAR(64) NOT NULL,
 reason VARCHAR(32) NOT NULL,details VARCHAR(1000) NOT NULL,status VARCHAR(16) NOT NULL,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(book_id) REFERENCES books(id),FOREIGN KEY(reporter_user_id) REFERENCES users(id),INDEX reports_queue(status,created_at),INDEX reports_owner(reporter_user_id,created_at)
);
CREATE TABLE group_audit_events (
 id VARCHAR(36) PRIMARY KEY,book_id VARCHAR(36) NOT NULL,
 actor_user_id VARCHAR(64) NOT NULL,action VARCHAR(100) NOT NULL,target_type VARCHAR(16) NOT NULL,target_id VARCHAR(64) NOT NULL,
 reason VARCHAR(500) NOT NULL,created_at TIMESTAMP(3) NOT NULL,FOREIGN KEY(book_id) REFERENCES books(id),INDEX group_audit_time(book_id,created_at),INDEX audit_actor(actor_user_id,created_at)
);
CREATE TABLE group_events (
 sequence BIGINT AUTO_INCREMENT PRIMARY KEY,book_id VARCHAR(36) NOT NULL,
 message_id VARCHAR(36) NOT NULL,event_type VARCHAR(16) NOT NULL,created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(book_id) REFERENCES books(id),INDEX group_event_cursor(book_id,sequence)
);

CREATE TABLE book_embeddings (
 book_id VARCHAR(36) NOT NULL,
 cache_key VARCHAR(64) NOT NULL,
 vector_json MEDIUMTEXT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(book_id,cache_key),FOREIGN KEY(book_id) REFERENCES books(id)
);

CREATE TABLE scene_images (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 book_id VARCHAR(36) NOT NULL,
 file_name VARCHAR(40) NOT NULL UNIQUE,
 retention_until TIMESTAMP(3) NOT NULL,created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(book_id) REFERENCES books(id),INDEX scene_expiry(retention_until),INDEX scene_owner(user_id,id)
);

ALTER TABLE book_import_jobs ADD COLUMN reading_mode VARCHAR(16) NOT NULL DEFAULT 'continuous';
ALTER TABLE book_import_jobs ADD COLUMN words_per_quest INT NOT NULL DEFAULT 500;

ALTER TABLE membership_redemption_codes ADD COLUMN bound_user_id VARCHAR(36) NULL;
CREATE TABLE activation_purchases (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 request_key VARCHAR(128) NOT NULL,
 email VARCHAR(254) NOT NULL,email_hash VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL,code_id VARCHAR(36) NULL,
 delivery_status VARCHAR(16) NOT NULL,delivery_mode VARCHAR(16) NOT NULL,
 sealed_code TEXT NULL,lease_id VARCHAR(36) NULL,lease_until TIMESTAMP(3) NULL,
 attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMP(3) NULL,
 code_expires_at TIMESTAMP(3) NULL,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE KEY activation_submission(user_id,request_key),INDEX activation_owner(user_id,created_at),INDEX activation_delivery(delivery_status,next_attempt_at),
 INDEX activation_email_limit(email_hash,created_at),FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TABLE agent_runs (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 request_key VARCHAR(128) NOT NULL,
 input_hash VARCHAR(64) NOT NULL,
 book_id VARCHAR(36) NOT NULL,
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
