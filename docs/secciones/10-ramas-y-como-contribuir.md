[⬅ Volver al README Principal](../../README.md)

---

## 10. RAMAS Y CÓMO CONTRIBUIR

El equipo de Portal8080 adopta el modelo de trabajo **GitFlow adaptado para microservicios**. Las ramas `main` y `develop` están protegidas y reciben cambios exclusivamente mediante Pull Requests validados por el pipeline automatizado.

### Estructura Oficial de Ramas

```text
main                    <-- Código en producción (Desplegado en Azure PROD - Sprint 2/3)
└── develop             <-- Rama de integración continua (Staging / QA)
    ├── feature/scaffolding                    <-- Chasis inicial, arquitectura hexagonal y Docker
    ├── feature/PT8080-<ID>-<descripcion>      <-- Nuevas funcionalidades del Sprint
    ├── bugfix/PT8080-<ID>-<descripcion>       <-- Corrección de defectos reportados
    └── hotfix/<descripcion-urgente>           <-- Parche crítico directo a producción
```

### Convención de Commits (*Conventional Commits*)

Todo mensaje de commit debe seguir la estructura estándar:

```text
<tipo>(<alcance>): <descripción imperativa en español> [#PT8080-XX]

Ejemplos válidos en ms-probador-virtual:
feat(vton): orquestar solicitud asíncrona de prueba virtual con FASHN AI [#PT8080-25]
fix(cuotas): validar saldo atómico de cuotas mensuales y diarias en Redis antes de encolar [#PT8080-25]
test(dominio): pruebas unitarias sobre agregado TryOnOutfit y consentimientos [#PT8080-38]
docs(readme): actualizar matriz de enlaces y badges conectados para Sprint 1
```

### Checklist Obligatorio para Pull Requests

- [x] La rama parte de la versión más reciente de `develop`.
- [x] El código compila limpiamente sin advertencias críticas con `./mvnw clean compile`.
- [x] Todas las pruebas automatizadas pasan en verde (`./mvnw clean verify`).
- [x] La cobertura de código en JaCoCo supera el 85% obligatorio (alcanzado 98.68% en SonarCloud).
- [x] No se introducen credenciales, secretos ni contraseñas en el código (uso estricto de `.env.example`).
- [x] Los contenedores OCI se construyen exitosamente con `docker compose build`.
- [ ] El PR cuenta con al menos un revisor aprobado del squad antes de mezclarse a `develop`.

---

[⬅ Volver al README Principal](../../README.md)
