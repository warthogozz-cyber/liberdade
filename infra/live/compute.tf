data "aws_caller_identity" "current" {}

data "aws_ssm_parameter" "al2023_arm64_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

locals {
  # D11 log groups. logs.tf (Task 12) creates the groups from these same names.
  log_group_names = {
    production = "/liberdade/production"
    staging    = "/liberdade/staging"
    caddy      = "/liberdade/caddy"
  }

  account_id = data.aws_caller_identity.current.account_id

  instance_policy = {
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadRuntimeParameters"
        Effect   = "Allow"
        Action   = ["ssm:GetParametersByPath", "ssm:GetParameters"]
        Resource = "arn:aws:ssm:sa-east-1:${local.account_id}:parameter/liberdade/*"
      },
      {
        Sid      = "DecryptWithProjectKey"
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = aws_kms_key.main.arn
      },
      {
        Sid      = "WriteContainerLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = [for name in values(local.log_group_names) : "arn:aws:logs:sa-east-1:${local.account_id}:log-group:${name}:*"]
      },
    ]
  }
}

resource "aws_iam_role" "instance" {
  name = "liberdade-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "instance" {
  name   = "liberdade-instance"
  role   = aws_iam_role.instance.id
  policy = jsonencode(local.instance_policy)
}

resource "aws_iam_instance_profile" "instance" {
  name = "liberdade-instance"
  role = aws_iam_role.instance.name
}

resource "aws_security_group" "instance" {
  name        = "liberdade-instance"
  description = "Web traffic to the Liberdade host"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP (Caddy redirect and ACME)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "liberdade-instance" }
}

resource "aws_instance" "host" {
  ami                    = nonsensitive(data.aws_ssm_parameter.al2023_arm64_ami.value)
  instance_type          = "t4g.small"
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.instance.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name
  monitoring             = true

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    compose_version = "v5.6.1"
    compose_sha256  = "42cfa75f51ad0ccf290bad37899e0038a8c10a0069e85233ecc1d53e3ff1347d"
    rds_bundle_url  = "https://truststore.pki.rds.amazonaws.com/sa-east-1/sa-east-1-bundle.pem"
    swap_size_mb    = 1024
    liberdade_root  = "/opt/liberdade"
  })

  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    encrypted             = true
    kms_key_id            = aws_kms_key.main.arn
    delete_on_termination = true
  }

  tags = { Name = "liberdade" }

  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "host" {
  domain   = "vpc"
  instance = aws_instance.host.id

  tags = { Name = "liberdade" }

  depends_on = [aws_internet_gateway.main]
}
