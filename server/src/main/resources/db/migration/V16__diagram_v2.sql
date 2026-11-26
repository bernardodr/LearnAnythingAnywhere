-- Diagrams move to prompt v2 (issue #78): more hands-on patterns (dragging, predict then reveal, build up), how to
-- drag on a phone, and readable text on any fill. Only routes still on v1 move: a version the owner chose on purpose
-- stays. Diagrams already written keep their page; the next ones are drawn from v2.
UPDATE model_routes SET prompt_version = 'v2' WHERE task = 'DIAGRAM' AND prompt_version = 'v1';
