# ==========================================
# Stage 1: Build & Package the Application
# ==========================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package executable jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Minimal & Secure Production Runtime
# ==========================================
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Run as non-privileged service user for container security
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Copy built artifact from builder stage
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

# Configuration and JVM flags optimized for container memory
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 --start-period=60s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
