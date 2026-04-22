# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM amazoncorretto:21-al2023 AS builder
WORKDIR /app

RUN dnf install -y tar gzip && dnf clean all

# Copy wrapper + POM first so the dependency layer is cached independently
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Build the fat JAR
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Explode into Spring Boot layers (dependencies → loader → snapshot-deps → app)
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM amazoncorretto:21-al2023 AS runtime
WORKDIR /app

RUN dnf install -y shadow-utils && dnf clean all

# Non-root user for least-privilege execution
RUN groupadd -r appgroup && useradd -r -g appgroup -s /sbin/nologin appuser

# Copy layers in order — least-volatile first so Docker reuses cache efficiently
COPY --from=builder /app/target/extracted/dependencies/          ./
COPY --from=builder /app/target/extracted/spring-boot-loader/   ./
COPY --from=builder /app/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/target/extracted/application/           ./

USER appuser

EXPOSE 8080

# JVM tuning: ZGC with generational mode, cap heap at 75 % of container memory.
# JAVA_TOOL_OPTIONS is picked up automatically by the JVM — no need to wrap ENTRYPOINT in a shell.
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# start-period gives Flyway time to run migrations before health checks begin
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
