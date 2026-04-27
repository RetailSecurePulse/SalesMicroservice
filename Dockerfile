############################
# Stage 1: Build
############################
FROM maven:3.9.9-eclipse-temurin-23 AS builder

WORKDIR /build

# Copy your project files into the container
COPY . .

# Build the Spring Boot fat jar
RUN mvn clean package -DskipTests

############################
# Stage 2: Runtime
############################
FROM eclipse-temurin:23-jre-noble

# Install curl + tzdata (Debian-based image)
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl tzdata ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:${PATH}" \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

WORKDIR /app

# Copy jar from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Create non-root user for runtime
RUN groupadd -r appgroup && useradd -r -g appgroup appuser && chown -R appuser:appgroup /app
USER appuser

# Expose port
EXPOSE 8085

# Start the service
ENTRYPOINT ["java", "-jar", "app.jar"]
