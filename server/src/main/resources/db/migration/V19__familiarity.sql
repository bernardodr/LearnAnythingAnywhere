-- How familiar the learner already is with a book's subject (issue #83): the slider when a book is started, 1 (brand
-- new) to 5 (very confident). Null for books started before it. Given to the AI for the preface and every lesson.
ALTER TABLE textbooks ADD COLUMN familiarity smallint CHECK (familiarity BETWEEN 1 AND 5);

-- The prompts that use it move to their new versions: the preface (v3, both the interview and its draft), lesson
-- planning (v2) and lesson writing (v3). Only routes still on the version before move: an owner's choice stays.
UPDATE model_routes SET prompt_version = 'v3' WHERE task = 'MISSION_INTERVIEW' AND prompt_version = 'v2';
UPDATE model_routes SET prompt_version = 'v2' WHERE task = 'LESSON_PLAN' AND prompt_version = 'v1';
UPDATE model_routes SET prompt_version = 'v3' WHERE task = 'LESSON_WRITE' AND prompt_version = 'v2';
