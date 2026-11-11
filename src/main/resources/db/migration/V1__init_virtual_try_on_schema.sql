-- ===================================================================
-- Esquema Inicial para el Microservicio de Probador Virtual (PT8080)
-- Bounded Context: Virtual Try-On · Squad Portal8080 (DOSW 2026)
-- ===================================================================

CREATE TABLE IF NOT EXISTS try_on_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    garment_id UUID NOT NULL,
    photo_url TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_image_url TEXT,
    execution_time_ms BIGINT,
    error_message VARCHAR(1024),
    photo_purged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_try_on_jobs_user_id ON try_on_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_try_on_jobs_status ON try_on_jobs(status);

CREATE TABLE IF NOT EXISTS user_quotas (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    monthly_limit INT NOT NULL DEFAULT 10,
    used_count INT NOT NULL DEFAULT 0,
    billing_period VARCHAR(16) NOT NULL,
    last_reset_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_user_billing_period UNIQUE (user_id, billing_period)
);

CREATE INDEX IF NOT EXISTS idx_user_quotas_user_period ON user_quotas(user_id, billing_period);
