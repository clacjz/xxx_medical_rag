# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Configure Maven to use Aliyun mirror
COPY settings.xml /usr/share/maven/conf/settings.xml

# Copy pom.xml first to leverage Docker cache
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Create runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create app user with numeric UID (avoids group creation layer)
RUN groupadd -r appgroup && useradd -r -u 1000 -g appgroup appuser

# Copy the built JAR from builder
COPY --from=builder /app/target/*.jar ./app.jar

# Copy entrypoint and create directories/permissions in one layer
COPY docker/entrypoint.sh .
RUN chmod +x entrypoint.sh && \
    mkdir -p logs temp && \
    chown -R appuser:appgroup .

USER appuser

EXPOSE 18080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD wget -q -O- http://localhost:18080/api/v1/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
