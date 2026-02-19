-- Migration script for Post MongoDB Hybrid Storage
-- This script removes raw and html fields from posts table and adds isArchived field
-- Development phase: Direct schema changes without data migration

-- Drop raw and html columns (content now stored in MongoDB)
ALTER TABLE posts DROP COLUMN IF EXISTS raw;
ALTER TABLE posts DROP COLUMN IF EXISTS html;

-- Add isArchived column
ALTER TABLE posts ADD COLUMN is_archived BOOLEAN DEFAULT FALSE;

-- Create index on isArchived for efficient querying
CREATE INDEX idx_posts_is_archived ON posts(is_archived);
