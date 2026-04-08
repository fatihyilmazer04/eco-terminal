# =============================================================================
# Backend Dockerfile — Multi-stage build
# Stage 1: Maven build | Stage 2: JRE 21 runtime
# =============================================================================

# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Bağımlılıkları önce kopyala — Docker layer cache kullanımı
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Kaynak kodunu kopyala ve build et
COPY src/ src/
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Güvenlik: root olmayan kullanıcı
RUN addgroup -S ecoterminal && adduser -S ecoterminal -G ecoterminal
USER ecoterminal

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
