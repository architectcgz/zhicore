-- =====================================================
-- ZhiCore Microservices Database Initialization Script
-- 知构微服务系统数据库初始化脚本
-- 
-- 说明：此脚本由 Docker 容器首次启动时自动执行
-- 位置：/docker-entrypoint-initdb.d/
-- =====================================================

-- 创建所有微服务数据库
SELECT 'CREATE DATABASE zhicore_user' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'zhicore_user')\gexec
SELECT 'CREATE DATABASE zhicore_content' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'zhicore_content')\gexec
SELECT 'CREATE DATABASE zhicore_comment' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'zhicore_comment')\gexec
SELECT 'CREATE DATABASE zhicore_message' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'zhicore_message')\gexec
SELECT 'CREATE DATABASE zhicore_notification' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'zhicore_notification')\gexec
SELECT 'CREATE DATABASE zhicore_upload' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'zhicore_upload')\gexec
SELECT 'CREATE DATABASE zhicore_admin' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'zhicore_admin')\gexec
