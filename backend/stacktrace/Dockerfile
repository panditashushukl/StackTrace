# ---- Build Stage ----
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache Maven dependencies before copying source code
COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B \
    && find target -maxdepth 1 -name "*.jar" ! -name "*.jar.original" ! -name "*-plain.jar" -exec cp {} target/app.jar \;

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/app.jar /app/app.jar
RUN chown appuser:appgroup /app/app.jar
USER appuser

# Tuned for 512 MB RAM containers (e.g. Render free tier)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
