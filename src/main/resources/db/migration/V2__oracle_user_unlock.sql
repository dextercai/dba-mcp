ALTER TABLE database_detail ADD COLUMN user_unlock_enabled INTEGER NOT NULL DEFAULT 0 CHECK (user_unlock_enabled IN (0, 1));
