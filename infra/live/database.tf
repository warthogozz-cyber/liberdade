resource "aws_db_subnet_group" "main" {
  name       = "liberdade"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_security_group" "database" {
  name        = "liberdade-database"
  description = "PostgreSQL from the Liberdade host only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from the host"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.instance.id]
  }

  tags = { Name = "liberdade-database" }
}

resource "aws_db_parameter_group" "main" {
  name   = "liberdade-postgres18"
  family = "postgres18"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "liberdade"
  engine         = "postgres"
  engine_version = "18"
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true
  kms_key_id        = aws_kms_key.main.arn

  db_name                       = "postgres"
  username                      = "liberdade_admin"
  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.main.key_id

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  parameter_group_name   = aws_db_parameter_group.main.name
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = 7
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "liberdade-final"
  copy_tags_to_snapshot      = true
  auto_minor_version_upgrade = true

  tags = { Name = "liberdade" }
}
