# ---------- Build stage ----------
FROM eclipse-temurin:17.0.19_10-jdk-jammy AS builder

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -q dependency:go-offline

COPY src src

RUN ./mvnw -q clean package -DskipTests


# ---------- Runtime stage ----------
FROM eclipse-temurin:17.0.19_10-jre-jammy

WORKDIR /app

# curl is required by the Docker Compose health check
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user
RUN groupadd --system appgroup \
    && useradd --system \
        --gid appgroup \
        --create-home \
        appuser

COPY --from=builder \
    --chown=appuser:appgroup \
    /app/target/*.jar \
    /app/app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]