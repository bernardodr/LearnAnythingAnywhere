CREATE TABLE users (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email          text NOT NULL UNIQUE CHECK (email = lower(email)),
  display_name   text,
  role           text NOT NULL CHECK (role IN ('LEARNER', 'PARENT', 'OWNER')),
  parent_user_id uuid REFERENCES users (id),
  profile_id     uuid, -- FK to generation_profiles is added when that table arrives (M6)
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
