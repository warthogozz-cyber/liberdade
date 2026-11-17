output "instance_id" {
  value = aws_instance.host.id
}

output "elastic_ip" {
  value = aws_eip.host.public_ip
}

output "rds_endpoint" {
  value = aws_db_instance.main.endpoint
}

output "kms_key_arn" {
  value = aws_kms_key.main.arn
}

output "rds_master_secret_arn" {
  value = try(aws_db_instance.main.master_user_secret[0].secret_arn, null)
}
