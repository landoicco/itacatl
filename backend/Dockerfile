# Compile and package Spring app
# Floating within Maven 3.9 and Java 21, so every build gets their patches. Dependabot ignores this
# image because its tag embeds the JDK (see Runtime versions in docs/ci.md)
# Runs on the build machine's own platform: the jar is the same on every CPU, so building the arm64
# image for AWS on an x86 machine emulates nothing but the final stage
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ ./src/
RUN mvn package -DskipTests

# Run packaged Spring app
FROM eclipse-temurin:21-jre-alpine AS runner
# Unprivileged user, so a compromised process does not run as root in the container
RUN addgroup -S app && adduser -S app -G app
# RDS's certificate authorities, so the aws profile can verify the database's certificate. A public
# file that every environment may carry; only the aws profile reads it. Pinned by its SHA-256, so a
# changed bundle fails the build instead of changing what the app trusts (docs/aws-profile.md)
ADD --chmod=644 --checksum=sha256:90a6f1a7978a1b1b096dca6c57511ca48d3b8cf052f8e704baf358f34ea2f21b \
    https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /etc/ssl/rds/global-bundle.pem
# The JVM sizes its heap from the container memory limit, but its default of 25% wastes most of it
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
COPY --from=builder /app/target/itacatl.jar itacatl.jar
USER app
ENTRYPOINT ["java", "-jar", "itacatl.jar"]
