-- ===================================================================
-- Migración V4: Columnas de Categoría y Proveedor de IA en Trabajos
-- Requerimiento: SR-RF-25 (Generación y Orquestación de Virtual Try-On)
-- ===================================================================

ALTER TABLE try_on_jobs
    ADD COLUMN IF NOT EXISTS garment_category VARCHAR(32) NOT NULL DEFAULT 'TOPS',
    ADD COLUMN IF NOT EXISTS ai_provider VARCHAR(32) NOT NULL DEFAULT 'FASHN_AI';
