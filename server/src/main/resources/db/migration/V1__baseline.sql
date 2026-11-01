-- Baseline: extensions and shared helpers used by later migrations.
-- gen_random_uuid() is built into PostgreSQL 13+, so no extension is needed for UUIDs.

-- Keeps updated_at current on every UPDATE. Attach per table:
--   CREATE TRIGGER t_set_updated_at BEFORE UPDATE ON <table>
--     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
