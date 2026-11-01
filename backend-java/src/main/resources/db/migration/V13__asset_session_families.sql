ALTER TABLE refresh_sessions ADD COLUMN family_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '';
UPDATE refresh_sessions SET family_id=id WHERE family_id='';
CREATE INDEX session_family ON refresh_sessions(user_id,family_id,revoked_at);
