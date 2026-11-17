locals {
  github_oidc_host = "token.actions.githubusercontent.com"

  deploy_trust_policy = {
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.github_oidc_host}:sub" = [
            "repo:${var.github_repository}:environment:staging",
            "repo:${var.github_repository}:environment:production",
          ]
          "${local.github_oidc_host}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  }

  deploy_policy = {
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "RunDeployScriptOnTheHost"
        Effect = "Allow"
        Action = ["ssm:SendCommand"]
        Resource = [
          aws_instance.host.arn,
          "arn:aws:ssm:sa-east-1::document/AWS-RunShellScript",
        ]
      },
      {
        # These two actions do not support resource-level permissions.
        Sid      = "ReadCommandResults"
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      },
    ]
  }
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://${local.github_oidc_host}"
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "deploy" {
  name                 = "liberdade-deploy"
  max_session_duration = 3600
  assume_role_policy   = jsonencode(local.deploy_trust_policy)
}

resource "aws_iam_role_policy" "deploy" {
  name   = "liberdade-deploy"
  role   = aws_iam_role.deploy.id
  policy = jsonencode(local.deploy_policy)
}
