# 6. A secret origin header between CloudFront and the backend, without TLS

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

CloudFront forwards `/api/*` to the backend's Elastic IP over plain HTTP ([ADR 0002](0002-low-cost-production-architecture.md)). Two problems follow:

1. **Any distribution reaches the backend.** Its security group admits CloudFront's origin-facing addresses, which serve every AWS customer. Anyone can point a distribution of their own at the instance and skip this one's settings.
2. **The hop is not encrypted.** Session cookies and sign-in passwords travel in clear text between CloudFront and the instance.

The constraints are the cost rules (a $30 monthly ceiling, pay-per-use only) and the optional domain: the marketplace can run on CloudFront's own `*.cloudfront.net` name, with no domain of its own.

## Decision

**A secret header that only this distribution adds, checked by the app** (`X-Origin-Verify`), and no TLS on the hop for now:

- Terraform generates the secret (`random_password`) and stores it as an SSM SecureString; CloudFront sends it as a custom origin header, and replaces a header of the same name that a viewer sends.
- `OriginVerificationFilter`, first of all filters, rejects any request without it. Only the instance's own health checks on localhost are exempt.
- Solves problem 1. It also lets the app trust the client address CloudFront appends to `X-Forwarded-For`, which sign-in rate limiting needs ([security](../security.md#sign-in-rate-limiting)).

Rejected:

- **TLS on the instance.** CloudFront only accepts an origin certificate from a public authority, for the name it connects to. The Elastic IP's AWS name cannot get one, so it needs the business's own domain, which is optional, plus Let's Encrypt on the instance with a DNS challenge. Every spot replacement would request a new certificate and run into Let's Encrypt's limits, unless the certificate were stored and shared. Many moving parts for one instance.
- **A CloudFront VPC origin**, with the backend in a private subnet: only this distribution could reach it, over AWS's network. But the instance then needs a NAT gateway (about $33/month) or a NAT instance to reach AWS's APIs, which is one more instance to patch and a single point of failure for ECR, logs and Secrets Manager. VPC endpoints for each service cost more still.
- **A load balancer with an ACM certificate.** The standard answer, and about $16/month: over the ceiling with everything else.

## Consequences

- **Only this distribution reaches the app.** A guess of a 48-character random secret is out of reach; a leak of it is fixed by replacing it (`terraform apply -replace`) and refreshing the instance.
- **The hop stays unencrypted.** AWS documents that traffic from CloudFront to an AWS origin travels over its own network, and that its network encrypts traffic leaving its facilities at the physical layer. It is not end-to-end TLS, and is accepted at this scale.
- **The secret is in the Terraform state**, which is encrypted and private ([bootstrap](../terraform.md)), and in CloudFront's configuration.
- **TLS can be added later without undoing this:** with a domain, a certificate on the instance and `https-only` to the origin. The header stays, because TLS alone does not tell this distribution from another.
