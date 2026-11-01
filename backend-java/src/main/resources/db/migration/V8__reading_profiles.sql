ALTER TABLE review_attempts ADD COLUMN semantic_verdict VARCHAR(16) NULL;
ALTER TABLE review_attempts ADD COLUMN semantic_score DOUBLE NULL;
CREATE TABLE reading_profiles (
 user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 current_lexile INT NOT NULL DEFAULT 500,comfort_zone_min INT NOT NULL DEFAULT 400,comfort_zone_max INT NOT NULL DEFAULT 550,
 i_plus_one_min INT NOT NULL DEFAULT 550,i_plus_one_max INT NOT NULL DEFAULT 650,total_reading_minutes DOUBLE NOT NULL DEFAULT 0,
 average_lookup_per_sentence DOUBLE NOT NULL DEFAULT 0,average_comprehension_score DOUBLE NOT NULL DEFAULT 0,reading_confidence DOUBLE NOT NULL DEFAULT 0.5,
 last_adjustment_reason VARCHAR(1000) NOT NULL DEFAULT '',last_adjusted_at TIMESTAMP(3) NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE book_metadata (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,title VARCHAR(1000) NOT NULL,author VARCHAR(500) NOT NULL,cover VARCHAR(2000) NOT NULL DEFAULT '',description TEXT NOT NULL,
 lexile INT NOT NULL,difficulty_level VARCHAR(16) NOT NULL,genre VARCHAR(120) NOT NULL,source_url VARCHAR(2000) NOT NULL,
 download_status VARCHAR(32) NOT NULL DEFAULT 'metadataOnly',linked_book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),FOREIGN KEY(linked_book_id) REFERENCES books(id),INDEX metadata_lexile(lexile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE calibration_sessions (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'active',
 current_step INT NOT NULL,estimated_lexile INT NOT NULL,final_profile_json LONGTEXT NULL,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),INDEX calibration_user(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE calibration_samples (
 session_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,step INT NOT NULL,sample_id VARCHAR(32) NOT NULL,lexile INT NOT NULL,text TEXT NOT NULL,
 started_at TIMESTAMP(3) NOT NULL,finished_at TIMESTAMP(3) NULL,reading_seconds INT NULL,lookup_count INT NULL,comprehension_score DOUBLE NULL,flow_score DOUBLE NULL,
 PRIMARY KEY(session_id,step),UNIQUE KEY calibration_sample(session_id,sample_id),FOREIGN KEY(session_id) REFERENCES calibration_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;