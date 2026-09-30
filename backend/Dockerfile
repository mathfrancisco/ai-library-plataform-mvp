# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src ./src
# BuildKit cache for ~/.m2: dependencies are downloaded once across builds.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests package \
    && java -Djarmode=tools -jar target/ai-library-backend-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
# curl for the compose healthcheck; a non-root user owning the data directories.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home --home-dir /home/app app \
    && mkdir -p /data/uploads /data/onnx-cache \
    && chown -R app /data
WORKDIR /app
# Layers from least to most frequently changed, so code changes do not re-upload dependencies.
COPY --from=build --chown=app /app/extracted/dependencies/ ./
COPY --from=build --chown=app /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app /app/extracted/application/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    APP_UPLOAD_DIR=/data/uploads \
    AI_EMBEDDING_CACHE_DIR=/data/onnx-cache \
    DJL_CACHE_DIR=/data/onnx-cache/djl
USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
