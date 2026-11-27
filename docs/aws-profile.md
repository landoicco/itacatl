# The `aws` profile

How the backend runs in AWS: where its configuration and credentials come from, how it signs in to RDS without a password, and how the connection is verified. The infrastructure around it is in the [infra plan](infra-plan.md) and the [`backend` module](../infra/modules/backend/README.md); the database roles are in the [tenant isolation guide](tenant-isolation.md#how-the-role-is-created).

The same image runs everywhere ([configuration per environment](../AGENTS.md#conventions)). The instance's user data sets `SPRING_PROFILES_ACTIVE=aws`, and `application-aws.properties` overrides what differs from docker compose.

## In one page

| What | Locally (docker compose) | In AWS (`aws` profile) |
|---|---|---|
| Flyway, as the tables' owner | `DB_USER` and `DB_PASSWORD` from `.env` | The master user's credentials, read from Secrets Manager at startup |
| The application, as `itacatl_app` | `DB_APP_PASSWORD` from `.env` | An IAM token from the instance's role, through the AWS JDBC wrapper |
| TLS to the database | None, on the compose network | Required by RDS, and verified: `sslmode=verify-full` against RDS's CA bundle |
| Logs | Colored text | ECS JSON, for CloudWatch Logs Insights |
| What the instance passes | n/a | `DB_HOST`, `DB_NAME`, `DB_MASTER_SECRET_ARN`, `AWS_REGION`: names and addresses, no secret |

**Why:** the runtime holds no database password. The application's credential is a token that lives 15 minutes and is useless outside the instance's role, and the master password, which RDS rotates every seven days, is read where it is kept, when it is needed. Nothing secret goes through Terraform, the user data or the image.

## How it is wired in

### AWS credentials and region

Every AWS call (Secrets Manager, the IAM token) goes through the AWS SDK's default chains. The credentials come from the instance's role, through the instance metadata service; the container is one network hop further from it than the instance, so the launch template allows two hops (IMDSv2 only). The region comes from `AWS_REGION`, which the user data sets. Nothing in the profile names a key, an account or a region.

### The master secret: Spring Cloud AWS

RDS keeps the master user's credentials in a Secrets Manager secret it manages: a JSON object with `username` and `password`. [Spring Cloud AWS](https://docs.awspring.io/spring-cloud-aws/docs/current/reference/html/index.html#secrets-manager-integration)'s Secrets Manager starter loads it as configuration, before the application context starts:

```properties
spring.cloud.aws.secretsmanager.enabled=true
spring.config.import=aws-secretsmanager:${DB_MASTER_SECRET_ARN}?prefix=database.master.
spring.flyway.user=${database.master.username}
spring.flyway.password=${database.master.password}
```

- **`spring.config.import`** is Spring Boot's mechanism for extra configuration sources; the starter registers the `aws-secretsmanager:` scheme. Each key of the secret's JSON becomes a property.
- **`?prefix=database.master.`** puts those properties under a namespace of their own (`database.master.username`), so a key named `password` cannot collide with anything else.
- **Read on every start, and only then.** Flyway needs the credentials only to migrate, at startup, so RDS's rotation is picked up by the next start, and a running instance never needs the new password.
- **Off outside AWS.** `application.properties` sets `spring.cloud.aws.secretsmanager.enabled=false`, so docker compose and the tests never try to reach AWS; only the `aws` profile turns it on.
- **Spring Cloud AWS's BOM** (`spring-cloud-aws-dependencies`, imported in `pom.xml`) pins the AWS SDK modules it is tested with, including `rds`, which the wrapper below uses.

### `itacatl_app` with an IAM token: the AWS JDBC wrapper

RDS's IAM authentication replaces a password with a token: a signed request, valid 15 minutes, that the instance's role generates locally and RDS checks. The role is allowed `rds-db:connect` for `itacatl_app` only, and `AppRoleCallback` grants the role `rds_iam` (with `itacatl.app-role.iam-authentication=true`), which tells RDS to accept tokens instead of a password for it.

HikariCP opens connections by itself, whenever it needs one, so the token must be generated at that moment, not once at startup. The [AWS Advanced JDBC Wrapper](https://github.com/aws/aws-advanced-jdbc-wrapper) does that: a JDBC driver that wraps PostgreSQL's, with plugins that act on each connection.

```properties
spring.datasource.url=jdbc:aws-wrapper:postgresql://${DB_HOST}:5432/${DB_NAME}?wrapperPlugins=iam&${itacatl.database.tls}
spring.datasource.driver-class-name=software.amazon.jdbc.Driver
spring.datasource.password=
```

- **`jdbc:aws-wrapper:postgresql:`** selects the wrapper, which hands the rest of the URL to pgjdbc.
- **`wrapperPlugins=iam`** generates a token with the AWS SDK's RDS module for every new connection and passes it as the password. The region is read from the RDS host name.
- **Expired tokens do not matter for open connections:** a token is checked only when a connection opens. Hikari retires connections after 30 minutes by default, and each new one gets a fresh token.
- **The username stays `itacatl_app`**, from `application.properties`; the empty password only replaces `DB_APP_PASSWORD`, which AWS does not set.

### Flyway keeps a plain URL

```properties
spring.flyway.url=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?${itacatl.database.tls}
```

Flyway migrates as the master user, with a password, so the wrapper would add nothing; and Flyway picks its database support from the URL, which it knows as `jdbc:postgresql:`. With its own URL, Flyway uses pgjdbc directly, and only the application's pool goes through the wrapper.

For the same reason Spring Session's schema initializer is turned off everywhere (`spring.session.jdbc.initialize-schema=never`): it would inspect the pool's URL, which it does not recognize. Flyway owns the session tables (`V3`) anyway.

### Verified TLS

```properties
itacatl.database.tls=sslmode=verify-full&sslrootcert=/etc/ssl/rds/global-bundle.pem
```

RDS requires TLS (`rds.force_ssl` in the [database module](../infra/modules/database/README.md)), but pgjdbc's default only encrypts: it does not check whom it is talking to. `verify-full` checks that the server's certificate is signed by RDS's certificate authorities and names the host in the URL, so the token and the master password only ever go to the real database.

The CA bundle is a public file that the Dockerfile adds to every image, pinned by its SHA-256 (`ADD --checksum`). If AWS changes the bundle, the build fails instead of quietly changing what the app trusts.

## Extending

- **A new secret for the application** (an API key, for example): keep it in Secrets Manager or as an SSM SecureString, created by Terraform; allow the backend's role to read that one resource in the [backend module](../infra/modules/backend/main.tf); pass its name or ARN through the user data; and load it in `application-aws.properties` with another `spring.config.import` entry and a prefix of its own. Locally, the same property comes from `.env`.
- **Updating the CA bundle**, when the build fails on its checksum: confirm the change against [AWS's certificate rotation notices](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL-certificate-rotation.html), then put the new digest in the Dockerfile (`curl -fsSL <url> | sha256sum`).
- **Another database user with IAM:** a `rds-db:connect` statement for that user in the backend's role, and `rds_iam` granted to it in the database.

## Known limitations

- **Nothing checks the profile before AWS.** The tests run against Testcontainers with passwords; the IAM sign-in, the secret and the certificate are first exercised on the first apply. The [backend module's first-apply checks](../infra/modules/backend/README.md#first-apply) list what to confirm.
- **A rotation in the middle of a start.** If RDS rotates the master password between reading the secret and Flyway connecting, that start fails; Docker restarts the container, which reads the new one.
