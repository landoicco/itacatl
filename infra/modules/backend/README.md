# backend

The Spring Boot backend on one spot Graviton instance, kept running by an Auto Scaling group, and the ECR repository its image comes from ([architecture](../../../docs/infra-plan.md#architecture)).

- **Spot only, one instance.** A mixed instances policy offers several 2 GB Graviton types in both public subnets, chosen by price and capacity. Capacity rebalancing launches a replacement when AWS recommends it, before the two-minute notice. There is no on-demand fallback ([cost rules](../../../docs/infra-plan.md#cost-rules)).
- **A stable origin for CloudFront.** Every instance gets a new public IP, so the module keeps an Elastic IP that each new instance takes for itself once the app answers its health check. A replacement launched while the old instance still runs takes the traffic only when it can serve it. The address costs the same as the public IPv4 an instance would have anyway.
- **Reachable only from CloudFront.** The security group admits CloudFront's origin-facing prefix list on 8080, and nothing else: no SSH, since Session Manager opens a shell through the instance's role. It starts connections only to the database (5432, the module adds the database's ingress rule too) and to HTTPS, where AWS's APIs are; without a NAT gateway or VPC endpoints they are reached over the internet.
- **No secret on the instance.** The user data passes only names and addresses. The app reads the master user's credentials from Secrets Manager to migrate, and signs in as `itacatl_app` with an IAM token; the role allows `rds-db:connect` for that user only.
- **Each permission names its resource.** Only ECR's sign-in applies to every resource, as AWS requires. The instance may associate the Elastic IP only with instances that carry its own profile.
- **Hardened instance:** IMDSv2 only, with a hop limit of 2 because the container is one hop further away; an encrypted root volume; T instances in standard credit mode, which throttles instead of billing CPU above the baseline.
- **Always the latest AMI.** The launch template names Amazon Linux 2023 for arm64 through its public SSM parameter, which EC2 resolves at each launch, so a replacement boots a patched system and a new AMI is not a Terraform change.
- **Deploys** push an immutable tag to ECR, write it to the SSM parameter and start an instance refresh, which replaces the instance: a few minutes without the backend, accepted with a single instance. Terraform creates the parameter with `none` and never changes it again. ECR keeps the last ten images.
- **Logs** go from Docker to CloudWatch, one stream per instance, kept 14 days.
- **Known limitation:** without a load balancer the group watches only EC2's status checks. A backend that never answers its health check keeps its instance, without the Elastic IP, until someone looks; the observability module adds the alarm.

```hcl
module "backend" {
  source     = "../../modules/backend"
  name       = "itacatl-prod"
  vpc_id     = module.network.vpc_id
  subnet_ids = module.network.public_subnet_ids

  database_security_group_id      = module.database.security_group_id
  database_address                = module.database.address
  database_name                   = module.database.database_name
  database_resource_id            = module.database.resource_id
  database_master_user_secret_arn = module.database.master_user_secret_arn
}
```

## Accepted Trivy findings

Each is ignored inline, next to the resource, with its reason:

- `AVD-AWS-0104`, egress to any address: HTTPS to AWS's public endpoints, whose addresses are not fixed.
- `AVD-AWS-0017`, a log group without a customer-managed key: such a key bills monthly; CloudWatch encrypts the logs with its own.
- `AVD-AWS-0033`, an ECR repository without a customer-managed key: the same reason; ECR encrypts the images with its own.

## First apply

A mocked provider cannot check these against AWS; confirm them on the first apply:

- The instance boots with the tag `none`, so its container fails to start until the first deploy; this is expected ([runbook](../../../docs/terraform.md)).
- `t4g.small`, `c6g.medium` and `c7g.medium` are offered as spot in both zones, and the group accepts `credit_specification` with the types that are not burstable.
- The 8 GB root volume is at least the AMI's snapshot size.
- The instance appears in Session Manager, and `/var/log/cloud-init-output.log` shows the container started and the address associated.
- The ECR pull works with `ecr:BatchGetImage` and `ecr:GetDownloadUrlForLayer` alone.
- The app migrates and signs in as `itacatl_app` (its logs in CloudWatch), and the Elastic IP's DNS name answers `/actuator/health` from CloudFront.

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
| [aws_autoscaling_group.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/autoscaling_group) | resource |
| [aws_cloudwatch_log_group.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_group) | resource |
| [aws_ecr_lifecycle_policy.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_lifecycle_policy) | resource |
| [aws_ecr_repository.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_repository) | resource |
| [aws_eip.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/eip) | resource |
| [aws_iam_instance_profile.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_instance_profile) | resource |
| [aws_iam_role.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role) | resource |
| [aws_iam_role_policy.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy) | resource |
| [aws_iam_role_policy_attachment.ssm](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment) | resource |
| [aws_launch_template.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/launch_template) | resource |
| [aws_security_group.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/security_group) | resource |
| [aws_ssm_parameter.image_tag](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter) | resource |
| [aws_vpc_security_group_egress_rule.https](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc_security_group_egress_rule) | resource |
| [aws_vpc_security_group_egress_rule.to_database](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc_security_group_egress_rule) | resource |
| [aws_vpc_security_group_ingress_rule.database_from_backend](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc_security_group_ingress_rule) | resource |
| [aws_vpc_security_group_ingress_rule.from_cloudfront](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc_security_group_ingress_rule) | resource |
| [aws_caller_identity.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/caller_identity) | data source |
| [aws_ec2_managed_prefix_list.cloudfront](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/ec2_managed_prefix_list) | data source |
| [aws_partition.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/partition) | data source |
| [aws_region.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/region) | data source |

## Inputs

| Name | Description | Type | Default | Required |
| ---- | ----------- | ---- | ------- | :------: |
| database\_address | Host name of the database. | `string` | n/a | yes |
| database\_master\_user\_secret\_arn | ARN of the Secrets Manager secret with the master user's credentials, which Flyway migrates with. | `string` | n/a | yes |
| database\_name | Name of the database the application uses. | `string` | n/a | yes |
| database\_resource\_id | Resource ID of the database, which rds-db:connect names to let the instance sign in as itacatl\_app. | `string` | n/a | yes |
| database\_security\_group\_id | Security group of the database, which this module adds the backend's ingress rule to. | `string` | n/a | yes |
| name | Prefix of every resource name, such as itacatl-prod. | `string` | n/a | yes |
| subnet\_ids | Public subnets the Auto Scaling group launches the instance in, one per availability zone. | `list(string)` | n/a | yes |
| vpc\_id | VPC of the backend's security group. | `string` | n/a | yes |
| image\_retention\_count | Images ECR keeps; older ones are deleted. Every image can be rebuilt from its commit. | `number` | `10` | no |
| instance\_types | Graviton instance types the spot instance may be, all with 2 GB or more. More types make it rarer for none to have spot capacity. | `list(string)` | ```[ "t4g.small", "c6g.medium", "c7g.medium" ]``` | no |
| log\_retention\_days | Days CloudWatch keeps the backend's logs. | `number` | `14` | no |
| root\_volume\_size | Size of the instance's root volume, in GB. | `number` | `8` | no |

## Outputs

| Name | Description |
| ---- | ----------- |
| autoscaling\_group\_name | Name of the Auto Scaling group, which a deploy starts an instance refresh on. |
| image\_tag\_parameter\_name | SSM parameter holding the image tag new instances run, which a deploy updates. |
| log\_group\_name | CloudWatch log group with the backend's output. |
| origin\_domain\_name | Public DNS name of the backend's Elastic IP, for CloudFront's origin. |
| public\_ip | The backend's Elastic IP. |
| repository\_url | URL of the ECR repository that deploys push the backend image to. |
| security\_group\_id | Security group of the backend instance. |
| subnet\_ids | Subnets the Auto Scaling group launches the instance in. |
<!-- END_TF_DOCS -->
