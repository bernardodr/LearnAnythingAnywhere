-- M6: generation profiles (BRIEF sections 4 and 8). One per learner; owner_user_id is who manages it: the learner
-- themselves, or the parent for a child's profile. Profiles for children under 13 exist only under a PARENT account.

CREATE TABLE generation_profiles (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id         uuid NOT NULL REFERENCES users (id),
  age                   integer CHECK (age BETWEEN 5 AND 18),    -- null = adult
  child_safe            boolean NOT NULL DEFAULT false,
  filters               jsonb NOT NULL DEFAULT '[]'::jsonb,       -- categories to leave out, plus free-text extras
  links_policy          text NOT NULL DEFAULT 'OFF' CHECK (links_policy IN ('OFF', 'ALLOWLIST', 'OPEN')),
  reveal_after_attempts integer CHECK (reveal_after_attempts BETWEEN 1 AND 10), -- null = never
  level                 text,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON generation_profiles
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE users ADD CONSTRAINT users_profile_fk FOREIGN KEY (profile_id) REFERENCES generation_profiles (id);
ALTER TABLE users ADD COLUMN pin_hash text;  -- a parent's PIN (BCrypt), locking their children's settings
ALTER TABLE textbooks ADD CONSTRAINT textbooks_profile_fk FOREIGN KEY (profile_id) REFERENCES generation_profiles (id);
