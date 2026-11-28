# database

The marketplace's PostgreSQL on RDS: one single-AZ instance in the private subnets, and the only component whose cost is never traded for safety, since its data is the one thing the environment cannot rebuild ([cost rules](../../../docs/infra-plan.md#cost-rules)).

- **Reachable from nothing yet.** Its security group has no rules: the backend module adds one ingress rule for its own security group. The database starts no connections, so it has no egress either.
- **No password goes through Terraform.** RDS creates the master password in Secrets Manager and rotates it; Flyway reads it there to migrate as the tables' owner. The application signs in as `itacatl_app` with an IAM token, which the backend creates before migrating ([how the role is created](../../../docs/tenant-isolation.md#how-the-role-is-created)).
- **TLS is required** (`rds.force_ssl`), so neither the tokens nor the master password travel unencrypted.
- **The data survives mistakes:** encrypted storage with AWS's own key (no monthly charge), seven days of automated backups, deletion protection, and a final snapshot on destroy. The snapshot's name has a random suffix, because snapshot names are unique in the account and each destroy leaves one behind; the suffix also changes when a new identifier, database name or master user makes RDS replace the instance.
- **Left out because they bill monthly:** a Multi-AZ standby, Enhanced Monitoring, a customer-managed KMS key, and storage autoscaling. Exporting its logs to CloudWatch and Performance Insights are left out too: the [observability](../observability/README.md) module alarms on the basic RDS metrics, which have no charge.

Destroying it takes two steps, in the [runbook](../../../docs/terraform.md#destroying-prod).

```hcl
module "database" {
  source     = "../../modules/database"
  name       = "itacatl-prod"
  vpc_id     = module.network.vpc_id
  subnet_ids = module.network.private_subnet_ids
}
```

## First apply

A mocked provider cannot check these against AWS; confirm them on the first apply:

- RDS offers PostgreSQL 18 in `us-east-1` (`aws rds describe-db-engine-versions --engine postgres --query 'DBEngineVersions[].EngineVersion'`), and the `postgres18` parameter group family exists.
- `db.t4g.micro` is offered in both of the network's zones.
- The instance shows its secret under *Configuration* in the console, and `rds.force_ssl` is `1` in its parameter group.

<!-- BEGIN_TF_DOCS -->
## Requirements

| Name | Version |
| ---- | ------- |
| terraform | ~> 1.17 |
| aws | >= 6.73 |
| random | >= 3.7 |

## Providers

| Name | Version |
| ---- | ------- |
| aws | >= 6.73 |
| random | >= 3.7 |

## Modules

No modules.

## Resources

| Name | Type |
| ---- | ---- |
| [aws_db_instance.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/db_instance) | resource |
| [aws_db_parameter_group.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/db_parameter_group) | resource |
| [aws_db_subnet_group.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/db_subnet_group) | resource |
| [aws_security_group.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/security_group) | resource |
| [random_id.final_snapshot](https://registry.terraform.io/providers/hashicorp/random/latest/docs/resources/id) | resource |

## Inputs

| Name | Description | Type | Default | Required |
| ---- | ----------- | ---- | ------- | :------: |
| name | Identifier of the database instance and prefix of its other resources, such as itacatl-prod. | `string` | n/a | yes |
| subnet\_ids | Private subnets the database can be placed in, one per availability zone. | `list(string)` | n/a | yes |
| vpc\_id | VPC of the database's security group. | `string` | n/a | yes |
| allocated\_storage | Storage of the database, in GB. | `number` | `20` | no |
| backup\_retention\_days | Days the automated backups are kept. | `number` | `7` | no |
| database\_name | Name of the database the application uses. | `string` | `"itacatl"` | no |
| deletion\_protection | Whether the database refuses to be deleted. Set it to false, and apply, only right before a destroy. | `bool` | `true` | no |
| instance\_class | Instance class of the database. | `string` | `"db.t4g.micro"` | no |
| master\_username | Master user, which owns the tables and runs the migrations. RDS keeps its password in Secrets Manager. | `string` | `"itacatl"` | no |

## Outputs

| Name | Description |
| ---- | ----------- |
| address | Host name of the database, for the backend's JDBC URL. |
| database\_name | Name of the database the application uses. |
| identifier | Identifier of the database instance, the dimension of its CloudWatch metrics. |
| master\_user\_secret\_arn | ARN of the Secrets Manager secret holding the master password, which the backend reads to migrate. |
| master\_username | Master user, which Flyway migrates as. |
| port | Port the database listens on. |
| resource\_id | Resource ID of the database, which IAM policies name in rds-db:connect to let a role sign in as a database user. |
| security\_group\_id | Security group of the database, which the backend module adds its ingress rule to. |
| subnet\_ids | Subnets the database can be placed in. |
<!-- END_TF_DOCS -->
