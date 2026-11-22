-- =====================================================================================
-- V10: Trazabilidad temporal de cambios de suscripción en user_quotas (SR-RF-108)
-- Almacena el timestamp del último evento de Pagos aplicado para descartar reentregas
-- fuera de orden y conservar el plan vigente al cambiar de período mensual.
-- =====================================================================================

ALTER TABLE user_quotas
    ADD COLUMN IF NOT EXISTS plan_updated_at TIMESTAMP WITH TIME ZONE;
