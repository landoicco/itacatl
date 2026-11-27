# 2. Low-cost production architecture

- **Status:** Accepted
- **Date:** 2026-10-16

## Context

The target customers are small businesses that cannot pay much for software. One deployment hosts the whole marketplace, so the fixed monthly cost must stay low while still using managed AWS services and Terraform properly.

## Decision

- **Compute:** an EC2 Auto Scaling group of one instance, spot only (mixed instances policy over several 2 GB Graviton types, capacity rebalancing), with no on-demand fallback. Spot Fleet is a legacy API, so it is not used.
- **Database:** RDS PostgreSQL, single-AZ, `db.t4g.micro`, automated backups, deletion protection and a final snapshot. Not in a container on the instance, so it survives spot reclaims and keeps managed backups. It is never cut back to save money.
- **Edge:** CloudFront serves the S3 frontend and routes `/api/*` to the instance. No load balancer (~$17/month).
- **Network:** the instance in a public subnet, reachable only from the CloudFront prefix list; RDS in private subnets. No NAT gateway (~$33/month).
- **Notifications:** SQS with a dead-letter queue and a Lambda that calls the WhatsApp API. Reservations stay synchronous, because they must check limited stock before confirming.

Estimated at ~$25–27/month if left running, under a hard ceiling of $30 (see [infra-plan.md](../infra-plan.md#cost-rules)).

## Consequences

- No high availability: a spot reclaim, a failure or a deploy causes a few minutes of downtime.
- Deploys use ASG instance refresh: immutable, not zero-downtime.
- The instance needs a public IPv4 address (~$4/month).
