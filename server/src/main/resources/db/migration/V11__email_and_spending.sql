-- Email verification and password reset, and daily spending caps.

-- When the user proved they read this address (a verification or reset link). Accounts made before verification
-- existed are treated as verified; a child counts as verified when their parent is.
ALTER TABLE users ADD COLUMN email_verified_at timestamptz;
UPDATE users SET email_verified_at = now();

-- One-use links sent by email. Only a SHA-256 of the token is stored, so the table can't be used to sign in.
CREATE TABLE email_tokens (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  purpose     text NOT NULL CHECK (purpose IN ('VERIFY', 'RESET')),
  token_hash  text NOT NULL UNIQUE,
  expires_at  timestamptz NOT NULL,
  used_at     timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX email_tokens_user ON email_tokens (user_id, purpose);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON email_tokens
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- A learner's spend today, checked before every model call.
CREATE INDEX model_runs_user_created_at ON model_runs (user_id, created_at DESC);
