# observability

Alarms that email when something in prod breaks, and one dashboard that graphs what they watch ([architecture](../../../docs/infra-plan.md#architecture)). It stays within CloudWatch's free tier: ten alarms, ten custom metrics, three dashboards.

- **Seven alarms, one per way the app fails:**

  | Alarm | Fires when |
  |---|---|
  | `backend-not-in-service` | The backend's group has had nothing in service for 20 minutes, which outlasts a deploy or a spot replacement; also when the group stops reporting |
  | `backend-memory` | The instance has used more than 90% of its memory for 15 minutes |
  | `backend-disk` | The root volume is more than 80% full |
  | `cloudfront-5xx` | More than 5% of CloudFront's responses have been server errors for 15 minutes |
  | `database-cpu` | The database's CPU has been above 80% for 15 minutes |
  | `database-cpu-credits` | The database, a burstable class, has fewer than 30 CPU credits left, after which it is held to its baseline |
  | `database-storage` | The database has less than 2 GB free |

  The thresholds are first guesses, to tune once real traffic shows what normal looks like.
- **Email through SNS**, both when an alarm fires and when it clears. AWS emails the address a confirmation link, and nothing arrives until it is followed. The first thousand emails a month are free.
- **Memory and disk come from the backend**, whose CloudWatch agent publishes them without a host dimension, so the alarms outlive an instance replacement ([backend module](../backend/README.md)). A hung app is replaced by the backend's own watchdog; these alarms only tell the maintainer.
- **One dashboard**, a graph per alarm with its threshold drawn in.
- **In `us-east-1` only.** CloudFront publishes its metrics there, and the module refuses another region, as the frontend's certificate already does.
- **Left out on purpose**, all billed or of little use at this traffic; candidates for after the launch are in the [status page](../../../docs/status.md#parked-ideas):
  - VPC flow logs and CloudFront access logs, billed per GB ingested or stored.
  - RDS Performance Insights and Enhanced Monitoring; CloudWatch's basic RDS metrics are what the alarms need.
  - Application metrics (JVM, HTTP, connection pool) through Micrometer, dozens of custom metrics at about $0.30 each a month.

```hcl
module "observability" {
  source                    = "../../modules/observability"
  name                      = "itacatl-prod"
  alarm_email               = var.alarm_email
  autoscaling_group_name    = module.backend.autoscaling_group_name
  backend_metrics_namespace = module.backend.metrics_namespace
  database_identifier       = module.database.identifier
  distribution_id           = module.frontend.distribution_id
}
```

## Accepted Trivy findings

Ignored inline, next to the resource, with its reason:

- `AVD-AWS-0095`, an SNS topic without encryption: CloudWatch can only publish to a topic encrypted with a customer-managed key, which bills monthly, and the alarms carry no secrets.

## First apply

A mocked provider cannot check these against AWS; confirm them on the first apply:

- The confirmation email arrives, and once its link is followed the subscription shows as confirmed (`aws sns list-subscriptions-by-topic`).
- Every alarm starts in `OK` or `INSUFFICIENT_DATA`, not `ALARM`, except `backend-not-in-service` while the group fills.
- The memory and disk alarms find the agent's metrics: their dimensions match what the agent publishes (the backend module's first-apply checks).
- The dashboard shows a line in each graph.
- `aws cloudwatch set-alarm-state --alarm-name itacatl-prod-backend-disk --state-value ALARM --state-reason test` sends an email, and the alarm returns to `OK` on its next evaluation.

<!-- BEGIN_TF_DOCS -->
## Requirements

| Name | Version |
| ---- | ------- |
| terraform | ~> 1.17 |
| aws | >= 6.73 |

## Providers

| Name | Version |
| ---- | ------- |
| aws | >= 6.73 |

## Modules

No modules.

## Resources

| Name | Type |
| ---- | ---- |
| [aws_cloudwatch_dashboard.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_dashboard) | resource |
| [aws_cloudwatch_metric_alarm.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_metric_alarm) | resource |
| [aws_sns_topic.alarms](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/sns_topic) | resource |
| [aws_sns_topic_subscription.email](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/sns_topic_subscription) | resource |
| [aws_region.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/region) | data source |

## Inputs

| Name | Description | Type | Default | Required |
| ---- | ----------- | ---- | ------- | :------: |
| alarm\_email | Address the alarms are sent to. AWS emails it a link that must be followed before any alarm arrives. | `string` | n/a | yes |
| autoscaling\_group\_name | Auto Scaling group of the backend. | `string` | n/a | yes |
| backend\_metrics\_namespace | CloudWatch namespace the backend instance's agent publishes its memory and disk to. | `string` | n/a | yes |
| database\_identifier | Identifier of the RDS instance. | `string` | n/a | yes |
| distribution\_id | ID of the CloudFront distribution that serves the app and the API. | `string` | n/a | yes |
| name | Prefix of every resource name, such as itacatl-prod. | `string` | n/a | yes |

## Outputs

| Name | Description |
| ---- | ----------- |
| alarm\_topic\_arn | SNS topic every alarm notifies, when it fires and when it clears. |
| alarms | Each CloudWatch alarm by name, with the namespace and dimensions of the metric it watches. |
| dashboard\_name | Name of the CloudWatch dashboard. |
<!-- END_TF_DOCS -->
