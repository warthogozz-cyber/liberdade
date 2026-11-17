# No customer KMS key: the project key policy only delegates to IAM, so CloudWatch Logs could not use it.
resource "aws_cloudwatch_log_group" "main" {
  for_each = local.log_group_names

  name              = each.value
  retention_in_days = 30
}
