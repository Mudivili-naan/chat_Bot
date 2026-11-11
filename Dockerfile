# Multi-stage Docker build for Spring Boot 21
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy maven wrapper and pom.xml
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build jar
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
