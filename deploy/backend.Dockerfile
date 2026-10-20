FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /source
COPY backend/distributed-document-search-engine-main/ ./
ARG SERVICE
RUN mvn -B -ntp -pl services/${SERVICE} -am package -DskipTests && \
    if [ "${SERVICE}" = "api-gateway" ]; then \
      cp services/${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar /app.jar; \
    else cp services/${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT-exec.jar /app.jar; fi
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar ./app.jar
RUN mkdir logs && chown app:app logs
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
