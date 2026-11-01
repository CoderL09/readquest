ALTER TABLE book_import_jobs ADD COLUMN phase VARCHAR(24) NOT NULL DEFAULT 'queued';
ALTER TABLE book_import_jobs ADD COLUMN progress INT NOT NULL DEFAULT 0;
UPDATE book_import_jobs SET phase=CASE WHEN status='processing' THEN 'parsing' ELSE status END,
 progress=CASE WHEN status='completed' THEN 100 WHEN status='processing' THEN 10 ELSE 0 END;
