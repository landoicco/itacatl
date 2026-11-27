# The production environment: every module wired together. Each module joins here in the PR that
# adds it, so this stack can always be planned as a whole.

locals {
  name = "${var.project}-prod"
}

module "network" {
  source = "../../modules/network"
  name   = local.name
}

module "database" {
  source     = "../../modules/database"
  name       = local.name
  vpc_id     = module.network.vpc_id
  subnet_ids = module.network.private_subnet_ids

  deletion_protection = var.database_deletion_protection
}

module "backend" {
  source     = "../../modules/backend"
  name       = local.name
  vpc_id     = module.network.vpc_id
  subnet_ids = module.network.public_subnet_ids

  database_security_group_id      = module.database.security_group_id
  database_address                = module.database.address
  database_name                   = module.database.database_name
  database_resource_id            = module.database.resource_id
  database_master_user_secret_arn = module.database.master_user_secret_arn
}
