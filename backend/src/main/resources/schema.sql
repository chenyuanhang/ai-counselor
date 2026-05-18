CREATE DATABASE IF NOT EXISTS ai_counselor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_counselor;

CREATE TABLE IF NOT EXISTS counselor_user (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(256) NULL,
    phone VARCHAR(64) NULL,
    avatar VARCHAR(512) NULL,
    user_type VARCHAR(32) NOT NULL DEFAULT 'PORTAL_USER',
    portal_user_id VARCHAR(128) NULL,
    last_login_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_portal_user_id (portal_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS counselor_conversation (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL DEFAULT '新会话',
    message_count INT NOT NULL DEFAULT 0,
    archived TINYINT(1) NOT NULL DEFAULT 0,
    last_message_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_archived_time (user_id, archived, last_message_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS counselor_message (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    attachments TEXT NULL,
    streaming TINYINT(1) NOT NULL DEFAULT 0,
    streaming_end TINYINT(1) NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_time (conversation_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
