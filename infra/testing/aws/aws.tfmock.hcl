# Values for the mocked AWS provider, shared by every test file that loads it with
# `mock_provider "aws" { source = "<path to this directory>" }`. The mock's random strings would
# make invalid ARNs, which the provider rejects, so whatever goes into an ARN is fixed here.

mock_data "aws_partition" {
  defaults = { partition = "aws" }
}

mock_data "aws_region" {
  defaults = { region = "us-east-1" }
}

mock_data "aws_caller_identity" {
  defaults = { account_id = "111111111111" }
}
