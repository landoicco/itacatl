locals {
  # Fixed by the application: the user it connects as (application.properties) and the port of the
  # aws profile's JDBC URLs, RDS's default.
  app_database_user = "itacatl_app"
  database_port     = 5432
  app_port          = 8080

  # Known before the group exists, because the launch template's user data names them and the
  # group depends on the launch template.
  autoscaling_group_name = "${var.name}-backend"
  lifecycle_hook_name    = "app-ready"

  # Where the CloudWatch agent publishes the instance's memory and disk, which EC2 does not report.
  metrics_namespace = "${var.name}/backend"

  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition
  region     = data.aws_region.current.region
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}
data "aws_region" "current" {}

# CloudFront's origin-facing addresses, kept up to date by AWS. The only source the instance
# admits, so nothing reaches it without going through CloudFront.
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# --- Images and deploys ---

# Encrypted with ECR's own key; a customer-managed one would bill monthly.
#trivy:ignore:AVD-AWS-0033 ECR's own encryption; a customer-managed key bills monthly
resource "aws_ecr_repository" "this" {
  name = "${var.name}-backend"
  # A tag always names the same image, so the tag in SSM says exactly what runs.
  image_tag_mutability = "IMMUTABLE"
  # Every image can be rebuilt from its commit, so a destroy does not stop at a non-empty repository.
  force_delete = true

  # Basic scanning, which has no charge.
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name

  # Tagged images are counted apart from untagged ones, so leftovers such as a build's attestation
  # never push a deployable tag out of the count.
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Delete untagged images after a week"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep the last ${var.image_retention_count} tagged images"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = var.image_retention_count
        }
        action = { type = "expire" }
      },
    ]
  })
}

# The tag the instance runs. A deploy changes it and starts an instance refresh; Terraform only
# creates it, so applying never rolls a deploy back.
resource "aws_ssm_parameter" "image_tag" {
  name        = "/${var.name}/backend/image-tag"
  description = "Tag of the backend image in ECR that new instances run"
  type        = "String"
  value       = "none"

  lifecycle {
    ignore_changes = [value]
  }
}

# --- The instance's address ---

# CloudFront needs an origin that does not change, and every instance the group launches has a
# new public IP. The instance takes this address once the app answers, so a replacement launched
# while the old one still runs (capacity rebalancing) takes over only when it is ready. It costs
# the same as the public IPv4 address an instance would have anyway.
resource "aws_eip" "this" {
  domain = "vpc"

  tags = { Name = "${var.name}-backend" }
}

# --- Network access ---

# Rules are separate resources below. Without egress blocks Terraform also removes AWS's default
# allow-all egress rule.
resource "aws_security_group" "this" {
  name        = "${var.name}-backend"
  description = "Backend instance, reachable only from CloudFront"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name}-backend" }
}

resource "aws_vpc_security_group_ingress_rule" "from_cloudfront" {
  security_group_id = aws_security_group.this.id
  description       = "The app, from CloudFront only"
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = local.app_port
  to_port           = local.app_port
}

# AWS's APIs (ECR, SSM, Secrets Manager, CloudWatch, EC2) and the AL2023 package repository, all
# over HTTPS. Their addresses are not fixed, and without a NAT gateway or VPC endpoints
# (cost rules) they are reached over the internet.
#trivy:ignore:AVD-AWS-0104 HTTPS to AWS's public endpoints, whose addresses are not fixed
resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.this.id
  description       = "AWS APIs and packages over HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "to_database" {
  security_group_id            = aws_security_group.this.id
  description                  = "PostgreSQL"
  referenced_security_group_id = var.database_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = local.database_port
  to_port                      = local.database_port
}

# Lives in this module, not the database's, so the database does not depend on its clients.
resource "aws_vpc_security_group_ingress_rule" "database_from_backend" {
  security_group_id            = var.database_security_group_id
  description                  = "PostgreSQL, from the backend"
  referenced_security_group_id = aws_security_group.this.id
  ip_protocol                  = "tcp"
  from_port                    = local.database_port
  to_port                      = local.database_port
}

# --- Logs ---

# The container's output, one stream per instance. Encrypted with CloudWatch's own key; a
# customer-managed one would bill monthly.
#trivy:ignore:AVD-AWS-0017 CloudWatch's own encryption; a customer-managed key bills monthly
resource "aws_cloudwatch_log_group" "this" {
  name              = "/${var.name}/backend"
  retention_in_days = var.log_retention_days
}

# --- The instance's permissions ---

resource "aws_iam_role" "this" {
  name = "${var.name}-backend"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Session Manager, instead of SSH and an open port 22.
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Each statement names the one resource it needs. Only the ECR token and the address association
# cannot be narrowed by resource alone.
resource "aws_iam_role_policy" "this" {
  name = "backend"
  role = aws_iam_role.this.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrSignIn"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid      = "PullTheImage"
        Effect   = "Allow"
        Action   = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
        Resource = aws_ecr_repository.this.arn
      },
      {
        Sid      = "ReadTheImageTag"
        Effect   = "Allow"
        Action   = "ssm:GetParameter"
        Resource = aws_ssm_parameter.image_tag.arn
      },
      {
        Sid      = "ReadTheMasterCredentials"
        Effect   = "Allow"
        Action   = "secretsmanager:GetSecretValue"
        Resource = var.database_master_user_secret_arn
      },
      {
        Sid      = "SignInAsTheAppRole"
        Effect   = "Allow"
        Action   = "rds-db:connect"
        Resource = "arn:${local.partition}:rds-db:${local.region}:${local.account_id}:dbuser:${var.database_resource_id}/${local.app_database_user}"
      },
      {
        Sid      = "WriteLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.this.arn}:*"
      },
      {
        # The instance takes the address for itself; the condition limits it to instances that
        # carry this role, so it cannot move the address anywhere else. EC2 may also authorize
        # the call against the instance's network interface, which has no such key.
        Sid    = "TakeTheAddress"
        Effect = "Allow"
        Action = "ec2:AssociateAddress"
        Resource = [
          "arn:${local.partition}:ec2:${local.region}:${local.account_id}:elastic-ip/${aws_eip.this.allocation_id}",
          "arn:${local.partition}:ec2:${local.region}:${local.account_id}:instance/*",
          "arn:${local.partition}:ec2:${local.region}:${local.account_id}:network-interface/*",
        ]
        Condition = {
          ArnEqualsIfExists = { "ec2:InstanceProfile" = aws_iam_instance_profile.this.arn }
        }
      },
      {
        Sid      = "PublishMemoryAndDisk"
        Effect   = "Allow"
        Action   = "cloudwatch:PutMetricData"
        Resource = "*"
        Condition = {
          StringEquals = { "cloudwatch:namespace" = local.metrics_namespace }
        }
      },
      {
        # Readiness to the launch hook, and a hung app from the watchdog
        Sid      = "ReportToTheGroup"
        Effect   = "Allow"
        Action   = ["autoscaling:CompleteLifecycleAction", "autoscaling:SetInstanceHealth"]
        Resource = "arn:${local.partition}:autoscaling:${local.region}:${local.account_id}:autoScalingGroup:*:autoScalingGroupName/${local.autoscaling_group_name}"
      },
    ]
  })
}

resource "aws_iam_instance_profile" "this" {
  name = "${var.name}-backend"
  role = aws_iam_role.this.name
}

# --- The instance ---

resource "aws_launch_template" "this" {
  name = "${var.name}-backend"
  # Amazon Linux 2023 for arm64, resolved by EC2 at each launch: every replacement boots the
  # latest patched AMI, and a new AMI does not show up as a Terraform change.
  image_id               = "resolve:ssm:/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
  update_default_version = true

  iam_instance_profile {
    name = aws_iam_instance_profile.this.name
  }

  # The subnets hand out no public IPs, but the instance needs one to reach AWS's APIs before it
  # takes the Elastic IP, which then replaces it.
  network_interfaces {
    associate_public_ip_address = true
    security_groups             = [aws_security_group.this.id]
    delete_on_termination       = true
  }

  # Tokens only (IMDSv2). Two hops, because the container is one more hop away from the
  # instance and the app's AWS SDK reads its credentials from there.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = var.root_volume_size
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  # T instances default to unlimited mode, which bills CPU above the baseline. Standard throttles
  # instead. Ignored by the types that are not burstable.
  credit_specification {
    cpu_credits = "standard"
  }

  user_data = base64encode(templatefile("${path.module}/user-data.sh.tftpl", {
    region              = local.region
    registry            = split("/", aws_ecr_repository.this.repository_url)[0]
    repository_url      = aws_ecr_repository.this.repository_url
    image_tag_parameter = aws_ssm_parameter.image_tag.name
    log_group           = aws_cloudwatch_log_group.this.name
    allocation_id       = aws_eip.this.allocation_id
    autoscaling_group   = local.autoscaling_group_name
    lifecycle_hook      = local.lifecycle_hook_name
    metrics_namespace   = local.metrics_namespace
    app_port            = local.app_port
    database_host       = var.database_address
    database_name       = var.database_name
    master_secret_arn   = var.database_master_user_secret_arn
  }))

  tag_specifications {
    resource_type = "instance"
    tags          = { Name = "${var.name}-backend" }
  }

  tag_specifications {
    resource_type = "volume"
    tags          = { Name = "${var.name}-backend" }
  }
}

# One instance, spot only. When it is reclaimed or fails, the group launches another, of any of
# the types, in any of the zones.
resource "aws_autoscaling_group" "this" {
  name                = local.autoscaling_group_name
  min_size            = 1
  max_size            = 1
  desired_capacity    = 1
  vpc_zone_identifier = var.subnet_ids
  # No load balancer: once in service, the group replaces instances that fail EC2's status checks,
  # and those whose watchdog (user data) reports that the app stopped answering.
  health_check_type = "EC2"
  # On AWS's rebalance recommendation, launch the replacement before the two-minute notice.
  capacity_rebalance = true

  # For the observability module's alarm on a group with nothing in service. Group metrics have no
  # charge.
  enabled_metrics     = ["GroupInServiceInstances"]
  metrics_granularity = "1Minute"

  # A new instance stays pending until its user data reports the app answering and the address
  # taken; only then is it in service, and only then does rebalancing terminate the old one.
  # Without a report in 15 minutes, or on a failure, the instance is abandoned: the group
  # terminates it and launches another. Declared here, not as a separate resource, so it already
  # holds for the group's first instance.
  initial_lifecycle_hook {
    name                 = local.lifecycle_hook_name
    lifecycle_transition = "autoscaling:EC2_INSTANCE_LAUNCHING"
    heartbeat_timeout    = 900
    default_result       = "ABANDON"
  }

  mixed_instances_policy {
    instances_distribution {
      on_demand_base_capacity                  = 0
      on_demand_percentage_above_base_capacity = 0
      spot_allocation_strategy                 = "price-capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.this.id
        version            = aws_launch_template.this.latest_version
      }

      dynamic "override" {
        for_each = var.instance_types
        content {
          instance_type = override.value
        }
      }
    }
  }

  # A change to the launch template replaces the instance. With one instance this means a few
  # minutes without the backend, accepted in the infra plan.
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 0
    }
  }

  tag {
    key                 = "Name"
    value               = "${var.name}-backend"
    propagate_at_launch = true
  }
}
