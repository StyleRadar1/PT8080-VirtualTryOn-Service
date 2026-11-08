-- ===================================================================
-- Migración V3: Normalización de URLs de Siluetas Biométricas Antiguas (SR-RF-23)
-- Elimina parámetros query SAS temporales (?se=...&sig=...) y esquemas absolutos
-- en la columna photo_url, persistiendo exclusivamente la ruta canónica relativa
-- del blob en Azure Blob Storage (ej: userId/silhouette-UUID.png).
-- ===================================================================

UPDATE biometric_photos
SET photo_url = REGEXP_REPLACE(
    REGEXP_REPLACE(photo_url, '\?.*$', ''),
    '^https?://[^/]+/[^/]+/',
    ''
)
WHERE photo_url LIKE 'http%' OR photo_url LIKE '%?%';
