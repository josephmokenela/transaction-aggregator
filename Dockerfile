# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

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
FROM eclipse-temurin:25-jdk AS runtime
WORKDIR /app

# Non-root user for least-privilege execution
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy layers in order — least-volatile first so Docker reuses cache efficiently
COPY --from=builder /app/target/extracted/dependencies/          ./
COPY --from=builder /app/target/extracted/spring-boot-loader/   ./
COPY --from=builder /app/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/target/extracted/application/           ./

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
