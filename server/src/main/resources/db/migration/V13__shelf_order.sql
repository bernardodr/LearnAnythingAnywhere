-- The order of books on a learner's shelf, as they arrange it by hand (0 = first). Null until they first arrange it;
-- unarranged books (and new ones) follow the arranged ones, by volume.
ALTER TABLE textbooks ADD COLUMN shelf_position integer;
