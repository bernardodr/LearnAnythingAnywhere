-- M3: lessons. `blocks`, `reference_sheet` and the text columns are client-safe (BRIEF section 7);
-- `rubrics` and `plan` are server-only and never leave the server.

CREATE TABLE lessons (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  textbook_id     uuid NOT NULL REFERENCES textbooks (id) ON DELETE CASCADE,
  seq             integer NOT NULL CHECK (seq > 0),
  title           text NOT NULL,
  subtitle        text NOT NULL,
  win             text NOT NULL,
  mission_link    text NOT NULL,
  blocks          jsonb NOT NULL,                -- client-safe lesson blocks, in the BRIEF section 7 shape
  reference_sheet jsonb,                         -- {title, md}
  rubrics         jsonb NOT NULL,                -- server-only: {blockId: {points: [...], answerOutline}}
  plan            jsonb NOT NULL,                -- server-only: the LESSON_PLAN output this lesson was written from
  status          text NOT NULL CHECK (status IN ('READY')),
  model_run_id    uuid,                          -- the LESSON_WRITE run that produced it
  read_at         timestamptz,                   -- set when the learner finishes it
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (textbook_id, seq)
);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON lessons
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE chat_messages ADD CONSTRAINT chat_messages_lesson_fk
  FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE;
