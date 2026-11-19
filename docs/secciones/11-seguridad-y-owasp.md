[⬅ Volver al README Principal](../../README.md)

---

## 11. SEGURIDAD, HABEAS DATA Y ANÁLISIS OWASP TOP 10:2025

El microservicio `PT8080-VirtualTryOn-Service` implementa las directrices oficiales de seguridad para datos biométricos, protección de privacidad (Ley 1581 de 2012 / Habeas Data) y mitigación de vulnerabilidades OWASP Top 10:2025.

* **Documento Técnico Oficial:** [`../security/SR_PT8080_Seguridad_y_OWASP_2025.md`](../security/SR_PT8080_Seguridad_y_OWASP_2025.md)

### Puntos de Control y Cumplimiento Normativo

1. **Protección de Datos Biométricos (Ley 1581 de 2012 - Habeas Data):**
   * **Consentimiento Previo y Explícito (SR-RF-23 / P5-N01):** Ninguna imagen es procesada sin una autorización activa y versionada en base de datos (`biometric_consents`).
   * **Derecho al Olvido (SR-RF-27):** Endpoint `DELETE /api/v1/try-on/personal-photos/{jobId}` que garantiza la destrucción física e irreversible de la fotografía del usuario y de los renders asociados tanto en PostgreSQL como en Azure Blob Storage.
   * **Purga Automática por Evento:** El consumidor AMQP `TryOnEventListenerAdapter` reacciona al evento `user.account.deleted` emitido por Auth para purgar de inmediato todo dato biométrico sin requerir intervención manual.

2. **Control de Acceso a Renders mediante SAS Tokens de 15 Minutos:**
   * La base de datos nunca almacena parámetros ni firmas SAS en sus tablas.
   * Al consultar los resultados (`GET /api/v1/try-on/jobs/{jobId}`), el servicio genera dinámicamente un token Service SAS temporal de solo lectura (`sp=r`) con validez estricta de **15 minutos** (`PT15M`), impidiendo accesos públicos no autorizados.

3. **Validación de Identidad y Roles (A01 - Broken Access Control):**
   * Inspección y validación estricta de tokens JWT Bearer (`HS256` con claves $\ge 256$ bits).
   * Verificación de titularidad (`userId` en ruta o cuerpo cotejado contra el claim `sub` / `userId` del token).
   * Soporte para identificadores numéricos y UUIDs canónicos.

4. **Resiliencia ante Proveedores Externos (A04 - Insecure Design):**
   * Cliente HTTP hacia FASHN AI protegido con timeouts acotados y Circuit Breakers de Resilience4j.
   * Manejo normalizado de fallos bajo el estándar RFC 7807 (Problem Details).

---

[⬅ Volver al README Principal](../../README.md)
