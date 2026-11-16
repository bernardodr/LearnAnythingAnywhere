-- Email and password sign-in. password_hash is Spring Security's tagged format ({argon2}$argon2id$v=19$m=…,t=…,p=…$salt$hash),
-- so the settings can be raised later and old hashes upgrade at their next sign-in. Null for accounts made before
-- passwords existed (and by dev sign-in); they can set one once signed in.
-- password_version goes up with every password change or reset. Each sign-in token carries the version it was issued
-- under, so a change ends every older sign-in.
ALTER TABLE users ADD COLUMN password_hash text;
ALTER TABLE users ADD COLUMN password_version integer NOT NULL DEFAULT 0;
