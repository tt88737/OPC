FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN mvn -B -DskipTests -pl opc-admin -am clean package

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 opc \
    && useradd --system --uid 10001 --gid opc --home-dir /app --shell /usr/sbin/nologin opc \
    && mkdir -p /app/logs /app/upload \
    && chown -R opc:opc /app

WORKDIR /app
COPY --from=build --chown=opc:opc /workspace/opc-admin/target/opc-admin.jar /app/opc-admin.jar

USER 10001:10001
EXPOSE 8080

ENV OPC_PROFILE=prod \
    OPC_LOG_PATH=/app/logs \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/opc-admin.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health > /dev/null || exit 1

