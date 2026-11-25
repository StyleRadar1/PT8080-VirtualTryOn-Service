-- BUG-019: Normalizar espacios en URLs de imágenes de prendas proyectadas y trabajos de Try-On
UPDATE catalog_garments
SET image_url = REPLACE(TRIM(image_url), ' ', '%20')
WHERE image_url IS NOT NULL
  AND (image_url <> TRIM(image_url) OR image_url LIKE '% %');

UPDATE try_on_jobs
SET garment_image_url = REPLACE(TRIM(garment_image_url), ' ', '%20')
WHERE garment_image_url IS NOT NULL
  AND (garment_image_url <> TRIM(garment_image_url) OR garment_image_url LIKE '% %');
