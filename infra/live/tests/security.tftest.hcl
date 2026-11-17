mock_provider "aws" {}

override_data {
  target = data.aws_ssm_parameter.al2023_arm64_ami
  values = {
    value = "ami-0123456789abcdef0"
  }
}

override_data {
  target = data.aws_availability_zones.available
  values = {
    names = ["sa-east-1a", "sa-east-1b", "sa-east-1c"]
  }
}

override_data {
  target = data.aws_caller_identity.current
  values = {
    account_id = "123456789012"
  }
}

# Computed attributes are unknown in a plan with a mock provider; give the ones the assertions compare.
override_resource {
  target = aws_kms_key.main
  values = {
    arn    = "arn:aws:kms:sa-east-1:123456789012:key/11111111-2222-3333-4444-555555555555"
    key_id = "11111111-2222-3333-4444-555555555555"
  }
}

override_resource {
  target = aws_security_group.instance
  values = {
    id = "sg-0aaaaaaaaaaaaaaaa"
  }
}

override_resource {
  target = aws_security_group.database
  values = {
    id = "sg-0bbbbbbbbbbbbbbbb"
  }
}

variables {
  alert_email       = "alerts@example.com"
  github_repository = "example-org/example-repo"
}

run "instance_requires_imdsv2" {
  command = plan

  assert {
    condition     = one(aws_instance.host.metadata_options).http_tokens == "required"
    error_message = "IMDSv2 must be required."
  }

  assert {
    condition     = one(aws_instance.host.metadata_options).http_put_response_hop_limit == 1
    error_message = "The metadata hop limit must be 1 so containers cannot reach it."
  }
}

run "instance_has_no_ssh_key" {
  command = plan

  assert {
    # Configuration lint: the mock provider fills the computed key_name with a random string, so the plan
    # value says nothing. No .tf file may assign key_name (a comment mentioning it is fine).
    condition = aws_instance.host.instance_type == "t4g.small" && alltrue([
      for f in fileset(path.module, "*.tf") : !can(regex("key_name\\s*=", file("${path.module}/${f}")))
    ])
    error_message = "The instance must have no key pair (access is by SSM only)."
  }
}

run "only_web_ports_open" {
  command = plan

  assert {
    condition = toset([
      for r in aws_security_group.instance.ingress : "${r.protocol}/${r.from_port}-${r.to_port}"
    ]) == toset(["tcp/80-80", "tcp/443-443"])
    error_message = "Ingress must be exactly TCP 80 and 443."
  }
}

run "root_volume_encrypted" {
  command = plan

  assert {
    condition     = one(aws_instance.host.root_block_device).encrypted == true
    error_message = "The root volume must be encrypted."
  }

  assert {
    condition     = one(aws_instance.host.root_block_device).kms_key_id == aws_kms_key.main.arn
    error_message = "The root volume must use the project key."
  }
}

run "database_is_private_and_encrypted" {
  command = plan

  assert {
    condition     = aws_db_instance.main.publicly_accessible == false
    error_message = "The database must not be public."
  }

  assert {
    condition     = aws_db_instance.main.storage_encrypted == true
    error_message = "The database storage must be encrypted."
  }

  assert {
    condition     = aws_db_instance.main.kms_key_id == aws_kms_key.main.arn
    error_message = "The database must use the project key."
  }

  assert {
    condition     = aws_db_instance.main.manage_master_user_password == true && aws_db_instance.main.master_user_secret_kms_key_id == aws_kms_key.main.key_id
    error_message = "The master password must be RDS-managed and encrypted with the project key."
  }
}

run "database_is_protected" {
  command = plan

  assert {
    condition     = aws_db_instance.main.deletion_protection == true
    error_message = "Deletion protection must be on."
  }

  assert {
    condition     = aws_db_instance.main.backup_retention_period == 7
    error_message = "Backups must be kept for 7 days."
  }

  assert {
    condition     = aws_db_instance.main.skip_final_snapshot == false
    error_message = "A final snapshot is required."
  }
}

run "database_forces_tls" {
  command = plan

  assert {
    condition     = one([for p in aws_db_parameter_group.main.parameter : p.value if p.name == "rds.force_ssl"]) == "1"
    error_message = "The parameter group must set rds.force_ssl = 1."
  }
}

run "database_reachable_only_from_instance" {
  command = plan

  assert {
    condition     = length(aws_security_group.database.ingress) == 1
    error_message = "The database SG must have a single ingress rule."
  }

  assert {
    condition = (
      one(aws_security_group.database.ingress).from_port == 5432 &&
      one(aws_security_group.database.ingress).to_port == 5432 &&
      one(aws_security_group.database.ingress).protocol == "tcp" &&
      one(aws_security_group.database.ingress).security_groups == toset([aws_security_group.instance.id]) &&
      (one(aws_security_group.database.ingress).cidr_blocks == null || length(one(aws_security_group.database.ingress).cidr_blocks) == 0)
    )
    error_message = "The database must accept only 5432 from the instance security group."
  }
}

run "key_rotation_enabled" {
  command = plan

  assert {
    condition     = aws_kms_key.main.enable_key_rotation == true
    error_message = "Key rotation must be enabled."
  }
}

run "instance_role_least_privilege" {
  command = plan

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.instance.policy).Statement :
      alltrue([for a in flatten([s.Action]) : a != "*" && !endswith(a, ":*")])
    ])
    error_message = "No statement may allow a wildcard action."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.instance.policy).Statement :
      alltrue([for r in flatten([s.Resource]) : r != "*"])
    ])
    error_message = "No statement may use Resource \"*\"."
  }

  assert {
    condition = toset(flatten([
      for s in jsondecode(aws_iam_role_policy.instance.policy).Statement : s.Action
    ])) == toset(["ssm:GetParametersByPath", "ssm:GetParameters", "kms:Decrypt", "logs:CreateLogStream", "logs:PutLogEvents"])
    error_message = "The inline policy must hold exactly the five approved actions."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.instance.policy).Statement :
      !contains(flatten([s.Action]), "kms:Decrypt") || s.Resource == aws_kms_key.main.arn
    ])
    error_message = "kms:Decrypt must be limited to the project key."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.instance.policy).Statement :
      !contains(flatten([s.Action]), "ssm:GetParameters") || s.Resource == "arn:aws:ssm:sa-east-1:123456789012:parameter/liberdade/*"
    ])
    error_message = "SSM reads must be limited to parameter/liberdade/*."
  }

  assert {
    condition = toset(flatten([
      for s in jsondecode(aws_iam_role_policy.instance.policy).Statement :
      s.Resource if contains(flatten([s.Action]), "logs:PutLogEvents")
      ])) == toset([
      "arn:aws:logs:sa-east-1:123456789012:log-group:/liberdade/production:*",
      "arn:aws:logs:sa-east-1:123456789012:log-group:/liberdade/staging:*",
      "arn:aws:logs:sa-east-1:123456789012:log-group:/liberdade/caddy:*",
    ])
    error_message = "Log writes must be limited to the three D11 groups."
  }

  assert {
    condition     = toset([aws_iam_role_policy_attachment.ssm_core.policy_arn]) == toset(["arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"])
    error_message = "Only AmazonSSMManagedInstanceCore may be attached as a managed policy."
  }
}

run "instance_gets_public_ip_at_launch" {
  command = plan

  # No NAT: without a public IP at launch, user_data cannot reach dnf or GitHub on first boot.
  assert {
    condition     = aws_instance.host.associate_public_ip_address == true
    error_message = "The instance must get a public IP at launch."
  }
}
