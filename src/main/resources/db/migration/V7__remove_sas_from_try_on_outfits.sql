-- Normalizar referencias en try_on_outfits para almacenar únicamente la URL permanente del blob sin parámetros SAS
UPDATE try_on_outfits SET final_render_url = split_part(final_render_url, '?', 1) WHERE final_render_url LIKE '%?%';

