# The production environment: every module wired together. Each module joins here in the PR that
# adds it, so this stack can always be planned as a whole.

locals {
  name = "${var.project}-prod"
}

module "network" {
  source = "../../modules/network"
  name   = local.name
}
