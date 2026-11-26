-- The reading ribbon (issue #81): when the learner last opened one of this book's lessons. The library puts its
-- ribbon on the book with the latest time, and only that one. Null until a lesson is opened.
ALTER TABLE textbooks ADD COLUMN last_read_at timestamptz;
