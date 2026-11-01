ALTER TABLE book_import_jobs ADD COLUMN reading_mode VARCHAR(16) NOT NULL DEFAULT 'continuous';
ALTER TABLE book_import_jobs ADD COLUMN words_per_quest INT NOT NULL DEFAULT 500;
