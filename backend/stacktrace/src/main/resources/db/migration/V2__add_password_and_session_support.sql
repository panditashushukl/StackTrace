ALTER TABLE users
    ALTER COLUMN google_id DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

ALTER TABLE stack_trace_analyses
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_stack_analyses_session ON stack_trace_analyses(session_id);
CREATE INDEX IF NOT EXISTS idx_stack_analyses_created_at ON stack_trace_analyses(created_at DESC);
