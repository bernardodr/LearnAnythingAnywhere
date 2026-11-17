-- Before the fix for parallel first requests, each could create its own reader settings for the same learner; only
-- the last was attached. The others belong to nobody: remove those no user or textbook points at.
DELETE FROM generation_profiles p
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.profile_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM textbooks t WHERE t.profile_id = p.id);
