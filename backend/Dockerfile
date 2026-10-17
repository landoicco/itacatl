# Compile and package Spring app
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ ./src/
RUN mvn package -DskipTests

# Run packaged Spring app
FROM eclipse-temurin:25-jre-alpine AS runner
# Unprivileged user, so a compromised process does not run as root in the container
RUN addgroup -S app && adduser -S app -G app
# The JVM sizes its heap from the container memory limit, but its default of 25% wastes most of it
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
COPY --from=builder /app/target/itacatl.jar itacatl.jar
USER app
ENTRYPOINT ["java", "-jar", "itacatl.jar"]
