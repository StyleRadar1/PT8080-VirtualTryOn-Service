-- ===================================================================
-- Migración V2: Tablas de Consentimiento Biométrico, Siluetas, Prendas de Catálogo, Outfits y Auditoría
-- Requerimientos: SR-RF-23 a SR-RF-27 · Ley 1581 de 2012 (Habeas Data)
-- ===================================================================

ALTER TABLE user_quotas
    ADD COLUMN IF NOT EXISTS is_radar_plus BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

CREATE TABLE IF NOT EXISTS biometric_consents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    terms_version VARCHAR(64) NOT NULL,
    accepted BOOLEAN NOT NULL DEFAULT TRUE,
    purposes TEXT NOT NULL,
    consented_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_biometric_consents_user_id ON biometric_consents(user_id);

CREATE TABLE IF NOT EXISTS biometric_photos (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    content_type VARCHAR(64) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    size_bytes BIGINT NOT NULL,
    photo_url TEXT NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_biometric_photos_user_id ON biometric_photos(user_id);

CREATE TABLE IF NOT EXISTS catalog_garments (
    garment_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(32) NOT NULL,
    image_url TEXT,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_garments_category ON catalog_garments(category);

CREATE TABLE IF NOT EXISTS try_on_outfits (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    final_render_url TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_try_on_outfits_user_id ON try_on_outfits(user_id);

CREATE TABLE IF NOT EXISTS job_audit_logs (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    transition_reason VARCHAR(512) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_job_audit_logs_job_id ON job_audit_logs(job_id);
