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

module "frontend" {
  source                     = "../../modules/frontend"
  name                       = local.name
  backend_origin_domain_name = module.backend.origin_domain_name
  backend_origin_port        = module.backend.origin_port
  domain                     = var.domain
}

module "observability" {
  source                    = "../../modules/observability"
  name                      = local.name
  alarm_email               = var.alarm_email
  autoscaling_group_name    = module.backend.autoscaling_group_name
  backend_metrics_namespace = module.backend.metrics_namespace
  database_identifier       = module.database.identifier
  distribution_id           = module.frontend.distribution_id
}
