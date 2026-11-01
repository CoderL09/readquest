CREATE TABLE group_memberships (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 role VARCHAR(16) NOT NULL,muted_until TIMESTAMP(3) NULL,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE KEY group_member(book_id,user_id),INDEX joined_groups(user_id,created_at),FOREIGN KEY(book_id) REFERENCES books(id),FOREIGN KEY(user_id) REFERENCES users(id),CHECK(role IN ('member','moderator','admin'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE group_messages (
 sequence BIGINT AUTO_INCREMENT PRIMARY KEY,id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,sender_user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 body TEXT NOT NULL,client_message_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 deleted_at TIMESTAMP(3) NULL,deleted_by_user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',deletion_reason VARCHAR(500) NOT NULL DEFAULT '',created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE KEY group_message_submission(book_id,sender_user_id,client_message_id),INDEX group_message_cursor(book_id,sequence),FOREIGN KEY(book_id) REFERENCES books(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE user_blocks (
 blocker_user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,blocked_user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,created_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(blocker_user_id,blocked_user_id),FOREIGN KEY(blocker_user_id) REFERENCES users(id),FOREIGN KEY(blocked_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE content_reports (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reporter_user_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,target_type VARCHAR(16) NOT NULL,
 target_message_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,target_user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(32) NOT NULL,details VARCHAR(1000) NOT NULL,status VARCHAR(16) NOT NULL,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(book_id) REFERENCES books(id),FOREIGN KEY(reporter_user_id) REFERENCES users(id),INDEX reports_queue(status,created_at),INDEX reports_owner(reporter_user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE group_audit_events (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,action VARCHAR(100) NOT NULL,target_type VARCHAR(16) NOT NULL,target_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(500) NOT NULL,created_at TIMESTAMP(3) NOT NULL,FOREIGN KEY(book_id) REFERENCES books(id),INDEX group_audit_time(book_id,created_at),INDEX audit_actor(actor_user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE group_events (
 sequence BIGINT AUTO_INCREMENT PRIMARY KEY,book_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 message_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,event_type VARCHAR(16) NOT NULL,created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(book_id) REFERENCES books(id),INDEX group_event_cursor(book_id,sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
