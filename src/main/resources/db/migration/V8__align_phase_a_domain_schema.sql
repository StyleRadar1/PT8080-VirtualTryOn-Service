-- =====================================================================================
-- V8: Alineamiento de esquema Fase A (SR-RF-23 / SR-RF-24 / SR-RF-25 / SR-RF-26)
-- - Agrega completed_at a try_on_jobs (timestamp de finalización exitosa)
-- - Agrega active a biometric_photos (estado activo de la foto biométrica)
-- - Normaliza categorías canónicas singulares (TOP, BOTTOM, ONE_PIECE) y default 'TOP'
-- =====================================================================================

ALTER TABLE try_on_jobs
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

UPDATE try_on_jobs
SET completed_at = updated_at
WHERE status = 'COMPLETED'
  AND completed_at IS NULL;

UPDATE try_on_jobs
SET garment_category = CASE garment_category
    WHEN 'TOPS' THEN 'TOP'
    WHEN 'BOTTOMS' THEN 'BOTTOM'
    WHEN 'ONE_PIECES' THEN 'ONE_PIECE'
    ELSE garment_category
END
WHERE garment_category IN ('TOPS', 'BOTTOMS', 'ONE_PIECES');

ALTER TABLE try_on_jobs
    ALTER COLUMN garment_category SET DEFAULT 'TOP';

UPDATE catalog_garments
SET category = CASE category
    WHEN 'TOPS' THEN 'TOP'
    WHEN 'BOTTOMS' THEN 'BOTTOM'
    WHEN 'ONE_PIECES' THEN 'ONE_PIECE'
    ELSE category
END
WHERE category IN ('TOPS', 'BOTTOMS', 'ONE_PIECES');

ALTER TABLE biometric_photos
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

