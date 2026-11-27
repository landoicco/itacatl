# Runs against a mocked AWS provider: no credentials, no account, nothing created.
# The modules test their own properties; these check how prod wires them together.

mock_provider "aws" {}

override_data {
  target = module.network.data.aws_availability_zones.available
  values = {
    names = ["us-east-1a", "us-east-1b", "us-east-1c"]
  }
}

run "network_spans_two_zones" {
  command = plan

  assert {
    condition     = module.network.availability_zones == tolist(["us-east-1a", "us-east-1b"])
    error_message = "Prod must spread its subnets over two availability zones."
  }
}
