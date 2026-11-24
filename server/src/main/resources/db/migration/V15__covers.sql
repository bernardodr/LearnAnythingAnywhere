-- The facelift's covers (docs/DESIGN.md "Books", issue #68).

-- 1. Each book's subject motif (com.laa.textbook.Motif, lower case), picked by the AI when the preface ends. Null
--    until then, and for books from before this: the server matches one from the title for those when it shows them.
ALTER TABLE textbooks ADD COLUMN cover_motif text CHECK (cover_motif ~ '^[a-z]+$');

-- 2. Cover cloths move to the new palette (forest, ochre, dusty blue, terracotta), by volume as Textbook.coverFor
--    gives them. Only the four old colours change.
UPDATE textbooks
SET cover_color = CASE (volume - 1) % 4 WHEN 0 THEN '#2F4A3B' WHEN 1 THEN '#D4A548' WHEN 2 THEN '#46668A' ELSE '#A9533A' END
WHERE cover_color IN ('#3E4C5E', '#5A6B4A', '#B4552F', '#23466B');

-- 3. The preface moves to prompt v2, whose draft picks the motif. Only routes still on v1 move: a version the owner
--    chose on purpose stays.
UPDATE model_routes SET prompt_version = 'v2' WHERE task = 'MISSION_INTERVIEW' AND prompt_version = 'v1';
