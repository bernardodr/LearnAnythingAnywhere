-- Sources for every lesson (issue #82): the book's sources (RESOURCES.md ids, r1, r2, …) found by web search and
-- checked for this lesson when it was written. Shown at the bottom of the lesson, with any others it cites. Lessons
-- written before this have none.
ALTER TABLE lessons ADD COLUMN source_ids text[] NOT NULL DEFAULT '{}';
