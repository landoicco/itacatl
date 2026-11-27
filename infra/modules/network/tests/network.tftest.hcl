# Runs against a mocked AWS provider: no credentials, no account, nothing created.

mock_provider "aws" {}

override_data {
  target = data.aws_availability_zones.available
  values = {
    names = ["us-east-1a", "us-east-1b", "us-east-1c"]
  }
}

variables {
  name = "itacatl-test"
}

run "subnets_span_two_zones" {
  command = plan

  assert {
    condition     = aws_subnet.public[*].availability_zone == ["us-east-1a", "us-east-1b"]
    error_message = "There must be one public subnet in each of the first two zones."
  }

  assert {
    condition     = aws_subnet.private[*].availability_zone == ["us-east-1a", "us-east-1b"]
    error_message = "There must be one private subnet in each of the first two zones, as RDS requires."
  }
}

run "subnet_ranges_do_not_overlap" {
  command = plan

  assert {
    condition = (
      aws_subnet.public[*].cidr_block == ["10.0.0.0/24", "10.0.1.0/24"] &&
      aws_subnet.private[*].cidr_block == ["10.0.128.0/24", "10.0.129.0/24"]
    )
    error_message = "Public subnets must take the low /24s of the VPC and private ones start at 128."
  }
}

run "only_public_subnets_reach_the_internet" {
  command = plan

  assert {
    condition     = [for route in aws_route_table.public.route : route.cidr_block] == ["0.0.0.0/0"]
    error_message = "The public route table must have a single default route, to the internet gateway."
  }

  assert {
    condition     = length(aws_route_table.private.route) == 0
    error_message = "The private route table must have no routes besides the implicit local one: no way out to the internet."
  }
}

run "no_public_ip_by_default" {
  command = plan

  assert {
    condition     = !anytrue(concat(aws_subnet.public[*].map_public_ip_on_launch, aws_subnet.private[*].map_public_ip_on_launch))
    error_message = "No subnet may assign public IPs on its own; the backend asks for one explicitly."
  }
}

run "default_security_group_allows_nothing" {
  command = plan

  assert {
    condition     = length(aws_default_security_group.this.ingress) == 0 && length(aws_default_security_group.this.egress) == 0
    error_message = "The default security group must have no rules."
  }
}

run "dns_is_enabled" {
  command = plan

  assert {
    condition     = aws_vpc.this.enable_dns_support && aws_vpc.this.enable_dns_hostnames
    error_message = "DNS support and hostnames must be on, so the RDS endpoint and SSM resolve."
  }
}

run "one_zone_is_rejected" {
  command = plan

  variables {
    az_count = 1
  }

  expect_failures = [var.az_count]
}

run "too_small_a_vpc_is_rejected" {
  command = plan

  variables {
    cidr_block = "10.0.0.0/24"
  }

  expect_failures = [var.cidr_block]
}
