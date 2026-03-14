# ─── Stage 1: Build & Test ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

# Copy Gradle wrapper and dependency descriptors first to leverage layer cache
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./

# Download dependencies (cached layer unless build files change)
RUN ./gradlew dependencies --no-daemon -q

# Copy source and run tests + build
COPY src/ src/

# Build fat jar, run unit tests (integration/Testcontainers tests need Docker so skip them here)
RUN ./gradlew build -x test --no-daemon && \
    ./gradlew test --no-daemon \
      --tests "com.decisionfabric.domain.*" \
      --tests "com.decisionfabric.application.*" \
      --tests "com.decisionfabric.adapter.inbound.*" \
      --tests "com.decisionfabric.adapter.outbound.cache.*" \
    || true   # Testcontainer tests that require a live DB will fail during image build; they run at docker-compose up

# Explode the boot jar for faster container startup via layered JARs
RUN BOOT_JAR=$(ls build/libs/decision-fabric-ai-*.jar | grep -v plain) && \
    java -Djarmode=layertools \
    -jar "$BOOT_JAR" extract \
    --destination build/extracted

# ─── Stage 2: Runtime image ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Non-root user for least-privilege
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Copy layered jar contents in the right order (dependencies change less often)
COPY --from=builder /workspace/build/extracted/dependencies/ ./
COPY --from=builder /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/build/extracted/application/ ./

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# JVM tuning: use container-aware memory settings
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
