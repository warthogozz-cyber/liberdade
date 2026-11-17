mock_provider "aws" {}

run "state_bucket_locked_down" {
  command = plan

  variables {
    state_bucket_name = "liberdade-tfstate-123456789012"
  }

  assert {
    condition     = aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled"
    error_message = "State bucket versioning must be enabled."
  }

  assert {
    condition = (
      aws_s3_bucket_public_access_block.state.block_public_acls &&
      aws_s3_bucket_public_access_block.state.block_public_policy &&
      aws_s3_bucket_public_access_block.state.ignore_public_acls &&
      aws_s3_bucket_public_access_block.state.restrict_public_buckets
    )
    error_message = "All four public access block options must be true."
  }

  assert {
    condition     = one(aws_s3_bucket_server_side_encryption_configuration.state.rule).apply_server_side_encryption_by_default[0].sse_algorithm == "AES256"
    error_message = "State bucket must be encrypted at rest."
  }

  assert {
    condition = anytrue([
      for s in jsondecode(aws_s3_bucket_policy.state.policy).Statement :
      s.Effect == "Deny" && s.Condition.Bool["aws:SecureTransport"] == "false" && s.Principal == "*"
    ])
    error_message = "State bucket policy must deny requests without TLS."
  }
}
