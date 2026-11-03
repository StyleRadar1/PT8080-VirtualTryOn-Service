# ===================================================================
# Dockerfile Multi-Stage para PT8080-VirtualTryOn-Service
# Squad Portal8080 · Proyecto StyleRadar (DOSW 2026)
# ===================================================================

# -------------------------------------------------------------------
# Etapa 1: Builder (Compilación y empaquetado del .jar con Maven)
# -------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copiar configuración de dependencias y wrapper para aprovechar la caché de Docker
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Dar permisos de ejecución al wrapper de Maven
RUN chmod +x mvnw

# Descargar dependencias en caché
RUN ./mvnw dependency:go-offline -B

# Copiar el código fuente y empaquetar la aplicación omitiendo tests en imagen base
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# -------------------------------------------------------------------
# Etapa 2: Runtime (Entorno de ejecución ultra-ligero y seguro)
# -------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="Squad Portal8080 - StyleRadar (DOSW 2026)"
LABEL description="Microservicio de Probador Virtual con IA (ms-probador-virtual · Puerto 8092)"

# Crear usuario y grupo sin privilegios para ejecución segura (no-root)
RUN addgroup -S styleradar && adduser -S styleradar -G styleradar

WORKDIR /app

# Copiar el binario ejecutable desde la etapa de compilación
COPY --from=builder --chown=styleradar:styleradar /build/target/*.jar app.jar

# Cambiar al usuario no privilegiado
USER styleradar:styleradar

# Puerto de escucha del microservicio
EXPOSE 8092

# Verificación de salud periódica mediante endpoint de Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8092/actuator/health || exit 1

# Parámetros optimizados para contenedores JVM en Java 21
ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dfile.encoding=UTF-8", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

