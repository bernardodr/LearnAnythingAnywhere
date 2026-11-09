-- M2: textbooks, their /teach workspace files, and the mission-interview chat.

CREATE TABLE textbooks (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid NOT NULL REFERENCES users (id),
  title            text NOT NULL,
  topic            text NOT NULL,                 -- what the learner typed
  profile_id       uuid,                          -- FK to generation_profiles arrives with that table (M6)
  status           text NOT NULL CHECK (status IN ('INTERVIEW', 'ACTIVE', 'ARCHIVED')),
  cover_color      text NOT NULL CHECK (cover_color ~ '^#[0-9A-F]{6}$'),
  volume           integer NOT NULL CHECK (volume > 0), -- "Vol. n" on the learner's shelf
  topic_gate       jsonb NOT NULL,                -- SAFETY_CHECK verdict at creation: {verdict, framing, message, runId}
  mission_draft    jsonb,                         -- latest interview draft, until confirmed
  mission_headline text,                          -- one line under the cover; MISSION.md itself stays in /teach format
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, volume)
);
CREATE INDEX textbooks_user ON textbooks (user_id, created_at);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON textbooks
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- The /teach workspace, one row per file (MISSION.md, RESOURCES.md, GLOSSARY.md, NOTES.md, learning-records/…).
CREATE TABLE workspace_files (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  textbook_id uuid NOT NULL REFERENCES textbooks (id) ON DELETE CASCADE,
  path        text NOT NULL,
  content     text NOT NULL,
  version     integer NOT NULL CHECK (version > 0),
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (textbook_id, path)
);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON workspace_files
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Every version ever written, including the current one.
CREATE TABLE workspace_file_versions (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  textbook_id uuid NOT NULL REFERENCES textbooks (id) ON DELETE CASCADE,
  path        text NOT NULL,
  version     integer NOT NULL CHECK (version > 0),
  content     text NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (textbook_id, path, version)
);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON workspace_file_versions
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE chat_messages (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  textbook_id uuid NOT NULL REFERENCES textbooks (id) ON DELETE CASCADE,
  lesson_id   uuid,                               -- FK to lessons arrives with that table (M3)
  kind        text NOT NULL CHECK (kind IN ('MISSION', 'TUTOR')),
  role        text NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
  content     text NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT clock_timestamp(), -- two rows written in one transaction keep their order
  updated_at  timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX chat_messages_thread ON chat_messages (textbook_id, kind, created_at);
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON chat_messages
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
