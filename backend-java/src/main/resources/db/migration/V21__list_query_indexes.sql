-- Preserve existing data. These indexes follow the equality predicates and stable list order.
CREATE INDEX library_user_order ON library_entries(user_id,updated_at DESC,book_id ASC);
CREATE INDEX lookups_vocabulary_order ON lookup_items(user_id,lookup_time DESC,id DESC,type);
CREATE INDEX review_evidence_lookup ON review_attempts(user_id,card_id,answer_kind,stage,grading_state,rating_applied);
