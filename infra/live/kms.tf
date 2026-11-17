# One customer-managed key for the project: EBS, RDS and its master secret. The default key policy delegates
# access to IAM in this account.
resource "aws_kms_key" "main" {
  description             = "liberdade project key"
  enable_key_rotation     = true
  rotation_period_in_days = 365
  deletion_window_in_days = 30
}

resource "aws_kms_alias" "main" {
  name          = "alias/liberdade"
  target_key_id = aws_kms_key.main.key_id
}
