-- Which call each run was (llm/Calls): "SAFETY_CHECK.topic", "SAFETY_CHECK.content", "GRADE", … The task alone can't
-- tell a topic-gate run from a content check. Null for runs written before this column existed.
ALTER TABLE model_runs ADD COLUMN call text;
