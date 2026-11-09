-- Las URL SAS son credenciales temporales y no deben permanecer en PostgreSQL.
UPDATE try_on_jobs SET photo_url = split_part(photo_url, '?', 1) WHERE photo_url LIKE '%?%';
UPDATE try_on_jobs SET result_image_url = split_part(result_image_url, '?', 1) WHERE result_image_url LIKE '%?%';
UPDATE biometric_photos SET photo_url = split_part(photo_url, '?', 1) WHERE photo_url LIKE '%?%';
