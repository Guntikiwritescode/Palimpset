# PALIMPSEST engine image (ARCHITECTURE §3.3, §5.6; WP5). Multi-stage:
# Maven+JDK 21 build -> JRE 21 runtime, non-root, actuator-based healthcheck.
#
# BUILD CONTEXT = REPO ROOT (not services/engine), because the engine's Maven
# build copies the FROZEN interchange schema from ../../contracts onto the
# classpath (services/engine/pom.xml copy-frozen-contracts). Build with:
#   docker build -f services/engine/Dockerfile -t palimpsest/engine:local .
# (the `make images` target does this).

# ---- build: Maven + JDK 21 -------------------------------------------------
# Pinned (no floating latest). Maven official image bundling Temurin JDK 21.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
# The frozen contracts must be present for the generate-resources copy step.
COPY contracts/ ./contracts/
COPY services/engine/ ./services/engine/
# -DskipTests: image build compiles + packages; tests run in the `engine` CI job
# (Testcontainers / PALIMPSEST_TEST_JDBC_URL), not during image assembly.
RUN mvn -q -B -f services/engine/pom.xml -DskipTests clean package \
 && cp services/engine/target/palimpsest-engine.jar /app.jar

# ---- runtime: JRE 21, non-root --------------------------------------------
# Pinned Temurin JRE (no floating latest).
FROM eclipse-temurin:21.0.5_11-jre-jammy AS runtime
# curl backs the actuator-based HEALTHCHECK below (not a bare TCP port check).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
# Non-root user matching the k8s securityContext (runAsUser: 10001) in
# deploy/base/palimpsest/engine-deployment.yaml.
RUN groupadd -g 10001 palimpsest \
 && useradd -u 10001 -g palimpsest -M -s /usr/sbin/nologin palimpsest
WORKDIR /opt/palimpsest
COPY --from=build /app.jar ./engine.jar
USER 10001:10001
EXPOSE 8080
# Healthcheck hits the actuator READINESS probe (management.endpoint.health.probes
# in application.yaml) — ready == DB reachable + migrations current — rather than a
# bare port check that would report a pre-migration pod as healthy.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=6 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "engine.jar"]
