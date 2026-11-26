-- BUG-019: Normalizar espacios y corregir URL corrupta específica (JUANNICOLASA%20Frontend-Style-Radar -> JUANNICOLASA/Frontend-Style-Radar)
UPDATE catalog_garments
SET image_url = REPLACE(REPLACE(REPLACE(TRIM(image_url), 'JUANNICOLASA%20Frontend-Style-Radar', 'JUANNICOLASA/Frontend-Style-Radar'), 'JUANNICOLASA Frontend-Style-Radar', 'JUANNICOLASA/Frontend-Style-Radar'), ' ', '%20')
WHERE image_url IS NOT NULL
  AND (image_url <> TRIM(image_url) 
       OR image_url LIKE '% %' 
       OR image_url LIKE '%JUANNICOLASA%20Frontend-Style-Radar%' 
       OR image_url LIKE '%JUANNICOLASA Frontend-Style-Radar%');

UPDATE try_on_jobs
SET garment_image_url = REPLACE(REPLACE(REPLACE(TRIM(garment_image_url), 'JUANNICOLASA%20Frontend-Style-Radar', 'JUANNICOLASA/Frontend-Style-Radar'), 'JUANNICOLASA Frontend-Style-Radar', 'JUANNICOLASA/Frontend-Style-Radar'), ' ', '%20')
WHERE garment_image_url IS NOT NULL
  AND (garment_image_url <> TRIM(garment_image_url) 
       OR garment_image_url LIKE '% %' 
       OR garment_image_url LIKE '%JUANNICOLASA%20Frontend-Style-Radar%' 
       OR garment_image_url LIKE '%JUANNICOLASA Frontend-Style-Radar%');
