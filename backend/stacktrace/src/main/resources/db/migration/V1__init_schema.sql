CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    avatar_url TEXT,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS stack_trace_analyses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    raw_stack_trace TEXT NOT NULL,
    code_context TEXT,
    detected_language VARCHAR(50) NOT NULL,
    error_type VARCHAR(255) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    analysis_result JSONB NOT NULL,
    model_used VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stack_analyses_user ON stack_trace_analyses(user_id);
CREATE INDEX IF NOT EXISTS idx_stack_analyses_lang_date ON stack_trace_analyses(detected_language, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stack_analyses_result_gin ON stack_trace_analyses USING GIN (analysis_result);
