-- Accounts are for adults (user decision, 2026-11-16). A child uses a parent's account, whose reader settings the
-- parent sets for them and locks with the parent PIN. Child accounts are removed with everything on their shelves
-- (user decision), and Parent and Learner become one kind of account.

CREATE TEMP TABLE child_accounts AS
  SELECT id, profile_id FROM users WHERE parent_user_id IS NOT NULL;

DELETE FROM attempts WHERE user_id IN (SELECT id FROM child_accounts);
DELETE FROM practice_progress WHERE user_id IN (SELECT id FROM child_accounts);
DELETE FROM textbooks WHERE user_id IN (SELECT id FROM child_accounts);  -- lessons, workspace files and chat go with them
DELETE FROM users WHERE id IN (SELECT id FROM child_accounts);            -- run log rows stay, without the user; email links go
DELETE FROM generation_profiles WHERE id IN (SELECT profile_id FROM child_accounts WHERE profile_id IS NOT NULL);
DROP TABLE child_accounts;

UPDATE users SET role = 'LEARNER' WHERE role = 'PARENT';
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('LEARNER', 'OWNER'));
ALTER TABLE users DROP COLUMN parent_user_id;

COMMENT ON COLUMN users.pin_hash IS 'The parent PIN (BCrypt): when set, changing this account''s reader settings, password or PIN needs it';
