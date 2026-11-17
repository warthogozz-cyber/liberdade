# Bootstraps the S3 bucket that holds the OpenTofu state of infra/live (D10). Run once, by the maintainer,
# with local state; the bucket name carries the account id so it is globally unique.
provider "aws" {
  region = "sa-east-1"

  default_tags {
    tags = {
      project = "liberdade"
    }
  }
}

variable "state_bucket_name" {
  description = "Globally unique name of the state bucket, for example liberdade-tfstate-<account id>."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{2,62}$", var.state_bucket_name))
    error_message = "Use 3 to 63 lowercase letters, digits, dots or hyphens."
  }
}

resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket_name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

locals {
  state_bucket_policy = {
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource  = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
        Condition = {
          Bool = { "aws:SecureTransport" = "false" }
        }
      }
    ]
  }
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = jsonencode(local.state_bucket_policy)

  depends_on = [aws_s3_bucket_public_access_block.state]
}

output "state_bucket_name" {
  value = aws_s3_bucket.state.id
}
