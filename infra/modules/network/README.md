# network

The VPC every other module runs in: public and private subnets spread over two availability zones, an internet gateway, and **no NAT gateway**.

- **Public subnets** hold the backend instance. Its security group only admits CloudFront, and it gets a public IP from its launch template, never from the subnet.
- **Private subnets** hold the database. Their route table has only the local route, so nothing in them can reach the internet or be reached from it.
- **The default security group has no rules**, so a resource created without one of its own gets no access.
- **Zones are standard ones, never `use1-az3`.** That older `us-east-1` zone lacks the newer instance types, Graviton included. It is excluded by zone ID, because each account maps zone names (`us-east-1a`) to physical zones differently.

Why there is no NAT gateway, load balancer or VPC endpoint is in the [infra plan's cost rules](../../../docs/infra-plan.md#cost-rules).

```hcl
module "network" {
  source = "../../modules/network"
  name   = "itacatl-prod"
}
```

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
| [aws_default_security_group.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/default_security_group) | resource |
| [aws_internet_gateway.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/internet_gateway) | resource |
| [aws_route_table.private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route_table) | resource |
| [aws_route_table.public](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route_table) | resource |
| [aws_route_table_association.private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route_table_association) | resource |
| [aws_route_table_association.public](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route_table_association) | resource |
| [aws_subnet.private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/subnet) | resource |
| [aws_subnet.public](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/subnet) | resource |
| [aws_vpc.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc) | resource |
| [aws_availability_zones.available](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/availability_zones) | data source |

## Inputs

| Name | Description | Type | Default | Required |
| ---- | ----------- | ---- | ------- | :------: |
| name | Prefix of every resource's Name tag, such as itacatl-prod. | `string` | n/a | yes |
| az\_count | Availability zones to spread the subnets over. | `number` | `2` | no |
| cidr\_block | IPv4 range of the VPC. Each subnet's prefix is 8 bits longer: a /24 in a /16. | `string` | `"10.0.0.0/16"` | no |

## Outputs

| Name | Description |
| ---- | ----------- |
| availability\_zones | Availability zones the subnets are spread over, in subnet order. |
| private\_subnet\_ids | IDs of the private subnets, one per zone, for the database's subnet group. |
| public\_subnet\_ids | IDs of the public subnets, one per zone, for the backend's Auto Scaling group. |
| vpc\_cidr\_block | IPv4 range of the VPC. |
| vpc\_id | ID of the VPC. |
<!-- END_TF_DOCS -->
