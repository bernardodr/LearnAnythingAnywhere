-- LLM gateway: providers, per-task routes, and one metering row per provider attempt.

CREATE TABLE providers (
  id           text PRIMARY KEY CHECK (id IN ('OPENAI', 'GOOGLE', 'ANTHROPIC', 'OLLAMA', 'FAKE', 'FAKE_B')),
  enabled      boolean NOT NULL DEFAULT true,
  is_default   boolean NOT NULL DEFAULT false,
  capabilities jsonb   NOT NULL DEFAULT '{}'::jsonb, -- what at least one of its models supports
  models       jsonb   NOT NULL DEFAULT '[]'::jsonb, -- [{id, label, tier, capabilities, pricing}], from application.yml
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);
-- At most one default provider.
CREATE UNIQUE INDEX providers_single_default ON providers (is_default) WHERE is_default;
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON providers
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE model_routes (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  task            text NOT NULL CHECK (task IN ('MISSION_INTERVIEW', 'RESEARCH', 'LESSON_PLAN', 'LESSON_WRITE',
                                                'TUTOR', 'GRADE', 'RECORD_UPDATE', 'SAFETY_CHECK')),
  provider_choice text NOT NULL DEFAULT 'DEFAULT'
                  CHECK (provider_choice IN ('DEFAULT', 'OPENAI', 'GOOGLE', 'ANTHROPIC', 'OLLAMA', 'FAKE', 'FAKE_B')),
  model_tier      text NOT NULL CHECK (model_tier IN ('LARGE', 'SMALL')),
  model_id        text, -- optional override of the tier
  params          jsonb NOT NULL DEFAULT '{}'::jsonb,
  prompt_version  text  NOT NULL DEFAULT 'v1',
  fallbacks       jsonb NOT NULL DEFAULT '[]'::jsonb, -- ordered provider ids
  traffic_split   jsonb,                               -- optional, not applied yet
  active          boolean NOT NULL DEFAULT true,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX model_routes_single_active_per_task ON model_routes (task) WHERE active;
CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON model_routes
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE model_runs (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  task           text NOT NULL,
  provider       text NOT NULL,
  model          text NOT NULL,
  prompt_version text,
  route_id       uuid REFERENCES model_routes (id) ON DELETE SET NULL,
  origin         text NOT NULL DEFAULT 'APP' CHECK (origin IN ('APP', 'COMPARE', 'DEV')),
  attempt        int  NOT NULL DEFAULT 1, -- position in the route's provider chain (1 = primary)
  tokens_in      int,
  tokens_out     int,
  cost_estimate  numeric(12, 6),          -- USD, rough
  latency_ms     int  NOT NULL,
  status         text NOT NULL CHECK (status IN ('OK', 'FALLBACK', 'ERROR', 'BLOCKED')),
  error_kind     text,                    -- TIMEOUT, RATE_LIMIT, SERVER_ERROR, PROVIDER_ERROR, INVALID_OUTPUT, CANCELLED
  error_message  text,                    -- always passed through the secret redactor
  safety_flags   jsonb NOT NULL DEFAULT '[]'::jsonb,
  textbook_id    uuid,                    -- FK added with textbooks (M2)
  user_id        uuid REFERENCES users (id) ON DELETE SET NULL,
  -- clock_timestamp(), not now(): rows written in one transaction still order correctly
  created_at     timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_at     timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX model_runs_created_at ON model_runs (created_at DESC);
CREATE INDEX model_runs_task_created_at ON model_runs (task, created_at DESC);
CREATE INDEX model_runs_provider_created_at ON model_runs (provider, created_at DESC);
