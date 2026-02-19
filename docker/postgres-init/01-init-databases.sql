-- =====================================================
-- Blog Microservices Database Initialization Script
-- 博客微服务系统数据库初始化脚本
-- 
-- 说明：此脚本由 Docker 容器首次启动时自动执行
-- 位置：/docker-entrypoint-initdb.d/
-- =====================================================

-- 创建所有微服务数据库
SELECT 'CREATE DATABASE blog_user' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'blog_user')\gexec
SELECT 'CREATE DATABASE blog_post' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'blog_post')\gexec
SELECT 'CREATE DATABASE blog_comment' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'blog_comment')\gexec
SELECT 'CREATE DATABASE blog_message' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'blog_message')\gexec
SELECT 'CREATE DATABASE blog_notification' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'blog_notification')\gexec
SELECT 'CREATE DATABASE blog_upload' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'blog_upload')\gexec
SELECT 'CREATE DATABASE blog_admin' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'blog_admin')\gexec
