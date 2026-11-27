-- ===================================================================
-- Migración V9: Agregar columna available_sizes a catalog_garments
-- Requerimiento: SR-RF-24 (Tallas disponibles de la prenda en catálogo)
-- ===================================================================

ALTER TABLE catalog_garments
    ADD COLUMN IF NOT EXISTS available_sizes VARCHAR(255);
