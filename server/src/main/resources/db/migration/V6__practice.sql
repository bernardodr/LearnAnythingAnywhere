-- M4: free-response practice. Every graded answer is an attempt; practice_progress tracks the hint ladder and
-- whether the block has been met, per learner.

CREATE TABLE attempts (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  lesson_id   uuid NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
  block_id    text NOT NULL,
  user_id     uuid NOT NULL REFERENCES users (id),
  answer      text NOT NULL,
  verdict     text NOT NULL CHECK (verdict IN ('MET', 'PARTIAL', 'NOT_YET')),
  feedback    jsonb NOT NULL,       -- the tutor's note as shown, plus leak-guard and run metadata
  attempt_no  integer NOT NULL CHECK (attempt_no > 0),
  created_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (lesson_id, block_id, user_id, attempt_no)
);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON attempts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE practice_progress (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  lesson_id      uuid NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
  block_id       text NOT NULL,
  user_id        uuid NOT NULL REFERENCES users (id),
  hints_revealed integer NOT NULL DEFAULT 0 CHECK (hints_revealed >= 0),
  answer_revealed boolean NOT NULL DEFAULT false,
  met_at         timestamptz,
  evidence_path  text,              -- the learning record written for it, if any
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  UNIQUE (lesson_id, block_id, user_id)
);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON practice_progress
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
