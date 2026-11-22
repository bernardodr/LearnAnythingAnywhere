-- Interactive diagrams in lessons (issue #60).
-- 1. DIAGRAM becomes a task the owner can route. Its route is added by RoutingSeeder at the next start.
ALTER TABLE model_routes DROP CONSTRAINT model_routes_task_check;
ALTER TABLE model_routes ADD CONSTRAINT model_routes_task_check
  CHECK (task IN ('MISSION_INTERVIEW', 'RESEARCH', 'LESSON_PLAN', 'LESSON_WRITE', 'DIAGRAM',
                  'TUTOR', 'GRADE', 'RECORD_UPDATE', 'SAFETY_CHECK'));

-- 2. Lesson writing moves to prompt v2, which can place DIAGRAM blocks. Only routes still on v1 move: a version the
--    owner chose on purpose stays.
UPDATE model_routes SET prompt_version = 'v2' WHERE task = 'LESSON_WRITE' AND prompt_version = 'v1';
