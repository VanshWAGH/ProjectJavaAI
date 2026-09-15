-- V4: Add per-user AI provider settings (BYOK - Bring Your Own Key)
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_provider  VARCHAR(30)  DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_model     VARCHAR(100) DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_api_key   TEXT         DEFAULT NULL;
