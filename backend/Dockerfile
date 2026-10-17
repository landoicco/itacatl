# Compile and package Spring app
# Floating within Maven 3.9 and Java 21, so every build gets their patches. Dependabot ignores this
# image because its tag embeds the JDK (see Runtime versions in docs/infra-plan.md)
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ ./src/
RUN mvn package -DskipTests

# Run packaged Spring app
FROM eclipse-temurin:21-jre-alpine AS runner
# Unprivileged user, so a compromised process does not run as root in the container
RUN addgroup -S app && adduser -S app -G app
# The JVM sizes its heap from the container memory limit, but its default of 25% wastes most of it
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
COPY --from=builder /app/target/itacatl.jar itacatl.jar
USER app
ENTRYPOINT ["java", "-jar", "itacatl.jar"]
