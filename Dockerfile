# ── Stage 1: build ──────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew .
RUN chmod +x gradlew

COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: runtime ────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
