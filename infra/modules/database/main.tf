locals {
  # The major version also names the parameter group's family. RDS applies minor versions itself,
  # in the maintenance window. Local development runs the same major version (docker-compose.yml).
  engine_major_version = "18"
}

resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids

  tags = { Name = "${var.name}-db" }
}

# No ingress rules here: the backend module adds one for its own security group, so this module
# does not depend on who connects to it. Without egress blocks Terraform also removes AWS's
# default allow-all egress rule; the database starts no connections.
resource "aws_security_group" "this" {
  name        = "${var.name}-db"
  description = "PostgreSQL, reachable only from the clients given an ingress rule"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name}-db" }
}

resource "aws_db_parameter_group" "this" {
  name_prefix = "${var.name}-postgres${local.engine_major_version}-"
  family      = "postgres${local.engine_major_version}"

  # Already RDS's default since PostgreSQL 15. Set here so a change of default cannot turn it off:
  # the application's IAM tokens and the master password must never travel unencrypted.
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  # A new major version needs a new family; the instance moves to the new group before the old
  # one is deleted.
  lifecycle {
    create_before_destroy = true
  }
}

# Snapshot names are unique in the account, and a destroyed database leaves its final snapshot
# behind: with a fixed name, the second destroy would fail. Each new database gets a new suffix:
# a new stack, and a change to an attribute that makes RDS replace the instance (the keepers).
resource "random_id" "final_snapshot" {
  byte_length = 4

  keepers = {
    identifier = var.name
    db_name    = var.database_name
    username   = var.master_username
  }
}

#trivy:ignore:AVD-AWS-0133 Database monitoring is decided with the observability module
resource "aws_db_instance" "this" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = local.engine_major_version
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage
  storage_type      = "gp3"
  # AWS's own key (aws/rds), which has no monthly charge, unlike a customer-managed one.
  storage_encrypted = true

  db_name  = var.database_name
  username = var.master_username
  # RDS creates the master password in Secrets Manager and rotates it; Terraform never sees it.
  manage_master_user_password = true
  # The application signs in as itacatl_app with a token from its IAM role, not a password.
  iam_database_authentication_enabled = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.this.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  # A standby would double the cost; the backups cover the failures a small marketplace accepts.
  multi_az = false

  # In UTC: 02:00 to 03:30 in Mexico City, when no business is open.
  backup_retention_period    = var.backup_retention_days
  backup_window              = "08:00-08:30"
  maintenance_window         = "sun:08:30-sun:09:30"
  copy_tags_to_snapshot      = true
  auto_minor_version_upgrade = true

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name}-final-${random_id.final_snapshot.hex}"

  tags = { Name = "${var.name}-db" }
}
