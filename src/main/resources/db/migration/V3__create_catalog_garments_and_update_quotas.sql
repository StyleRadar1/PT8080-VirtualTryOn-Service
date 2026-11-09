-- ===================================================================
-- Migración V3: Catálogo de Prendas y Cuotas Mensuales del Probador Virtual
-- Requerimiento: SR-RF-24 (Selección de Prendas y Cuotas Mensuales)
-- ===================================================================

ALTER TABLE user_quotas
    ADD COLUMN IF NOT EXISTS is_radar_plus BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

CREATE TABLE IF NOT EXISTS catalog_garments (
    garment_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(32) NOT NULL,
    image_url TEXT,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_garments_category ON catalog_garments(category);

