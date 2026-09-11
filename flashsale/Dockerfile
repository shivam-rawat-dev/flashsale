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

# Explicit heap allocation to prevent OOM under colocated task sizing (Exit code 137)
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
