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
  target = aws_instance.host
  values = {
    id  = "i-0123456789abcdef0"
    arn = "arn:aws:ec2:sa-east-1:123456789012:instance/i-0123456789abcdef0"
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

override_resource {
  target = aws_sns_topic.alerts
  values = {
    arn = "arn:aws:sns:sa-east-1:123456789012:liberdade-alerts"
  }
}

override_resource {
  target = aws_iam_openid_connect_provider.github
  values = {
    arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
  }
}

variables {
  alert_email       = "alerts@example.com"
  github_repository = "example-org/example-repo"
}

run "deploy_role_trusts_only_environments" {
  command = plan

  assert {
    condition     = one(aws_iam_openid_connect_provider.github.client_id_list) == "sts.amazonaws.com" && aws_iam_openid_connect_provider.github.url == "https://token.actions.githubusercontent.com"
    error_message = "The OIDC provider must be the GitHub one, with sts.amazonaws.com as the only audience."
  }

  assert {
    condition     = length(jsondecode(aws_iam_role.deploy.assume_role_policy).Statement) == 1
    error_message = "The trust policy must have a single statement."
  }

  assert {
    condition = (
      toset(flatten([jsondecode(aws_iam_role.deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"]])) ==
      toset(["repo:example-org/example-repo:environment:staging", "repo:example-org/example-repo:environment:production"])
    )
    error_message = "Only the staging and production Environment subjects may assume the role."
  }

  assert {
    condition     = jsondecode(aws_iam_role.deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:aud"] == "sts.amazonaws.com"
    error_message = "The audience must be sts.amazonaws.com."
  }

  assert {
    condition = (
      !contains(keys(jsondecode(aws_iam_role.deploy.assume_role_policy).Statement[0].Condition), "StringLike") &&
      jsondecode(aws_iam_role.deploy.assume_role_policy).Statement[0].Action == "sts:AssumeRoleWithWebIdentity" &&
      jsondecode(aws_iam_role.deploy.assume_role_policy).Statement[0].Principal.Federated == aws_iam_openid_connect_provider.github.arn
    )
    error_message = "Trust must be web identity from the GitHub provider, with no StringLike."
  }

  assert {
    condition     = aws_iam_role.deploy.max_session_duration == 3600
    error_message = "Sessions must last one hour."
  }
}

run "deploy_role_cannot_do_more" {
  command = plan

  assert {
    condition = toset(flatten([
      for s in jsondecode(aws_iam_role_policy.deploy.policy).Statement : s.Action
    ])) == toset(["ssm:SendCommand", "ssm:GetCommandInvocation", "ssm:ListCommandInvocations"])
    error_message = "The deploy role may only use the three approved SSM actions."
  }

  assert {
    condition = toset(flatten([
      for s in jsondecode(aws_iam_role_policy.deploy.policy).Statement :
      s.Resource if contains(flatten([s.Action]), "ssm:SendCommand")
      ])) == toset([
      "arn:aws:ec2:sa-east-1:123456789012:instance/i-0123456789abcdef0",
      "arn:aws:ssm:sa-east-1::document/AWS-RunShellScript",
    ])
    error_message = "SendCommand must be limited to the instance and the AWS-RunShellScript document."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.deploy.policy).Statement :
      contains(flatten([s.Action]), "ssm:SendCommand") ? !contains(flatten([s.Resource]), "*") : (
        s.Resource == "*" && toset(flatten([s.Action])) == toset(["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"])
      )
    ])
    error_message = "Only the two read-only invocation actions may use Resource \"*\"."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.deploy.policy).Statement :
      s.Effect == "Allow" && alltrue([for a in flatten([s.Action]) : a != "*" && !endswith(a, ":*")])
    ])
    error_message = "No wildcard actions."
  }
}

run "logs_kept_30_days" {
  command = plan

  assert {
    condition     = toset([for g in aws_cloudwatch_log_group.main : g.name]) == toset(["/liberdade/production", "/liberdade/staging", "/liberdade/caddy"])
    error_message = "The three D11 log groups must exist."
  }

  assert {
    condition     = alltrue([for g in aws_cloudwatch_log_group.main : g.retention_in_days == 30])
    error_message = "Log retention must be 30 days."
  }
}

run "alarms_cover_the_basics" {
  command = plan

  assert {
    condition = toset([
      aws_cloudwatch_metric_alarm.ec2_status.metric_name,
      aws_cloudwatch_metric_alarm.ec2_cpu.metric_name,
      aws_cloudwatch_metric_alarm.rds_storage.metric_name,
      aws_cloudwatch_metric_alarm.rds_cpu_credits.metric_name,
    ]) == toset(["StatusCheckFailed_System", "CPUUtilization", "FreeStorageSpace", "CPUCreditBalance"])
    error_message = "The four D11 alarms must exist."
  }

  assert {
    condition = alltrue([
      for a in [
        aws_cloudwatch_metric_alarm.ec2_status,
        aws_cloudwatch_metric_alarm.ec2_cpu,
        aws_cloudwatch_metric_alarm.rds_storage,
        aws_cloudwatch_metric_alarm.rds_cpu_credits,
      ] : contains(a.alarm_actions, aws_sns_topic.alerts.arn)
    ])
    error_message = "Every alarm must notify the alerts topic."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.rds_storage.threshold == 2147483648 && aws_cloudwatch_metric_alarm.rds_storage.comparison_operator == "LessThanThreshold"
    error_message = "Storage alarm: below 2 GiB free."
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.ec2_cpu.threshold == 80 &&
      aws_cloudwatch_metric_alarm.ec2_cpu.evaluation_periods == 3 &&
      aws_cloudwatch_metric_alarm.ec2_cpu.period == 300 &&
      aws_cloudwatch_metric_alarm.ec2_cpu.comparison_operator == "GreaterThanThreshold"
    )
    error_message = "CPU alarm: above 80% for 3 periods of 300 s."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.rds_cpu_credits.threshold == 30 && aws_cloudwatch_metric_alarm.rds_cpu_credits.comparison_operator == "LessThanThreshold"
    error_message = "Credit alarm: below 30."
  }

  assert {
    condition     = aws_sns_topic_subscription.email.protocol == "email" && aws_sns_topic_subscription.email.endpoint == "alerts@example.com"
    error_message = "The topic must send e-mail to alert_email."
  }
}

run "alarms_watch_the_right_resources" {
  command = plan

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.ec2_status.namespace == "AWS/EC2" &&
      aws_cloudwatch_metric_alarm.ec2_cpu.namespace == "AWS/EC2" &&
      aws_cloudwatch_metric_alarm.rds_storage.namespace == "AWS/RDS" &&
      aws_cloudwatch_metric_alarm.rds_cpu_credits.namespace == "AWS/RDS"
    )
    error_message = "Alarms must use the AWS/EC2 and AWS/RDS namespaces."
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.ec2_status.dimensions["InstanceId"] == "i-0123456789abcdef0" &&
      aws_cloudwatch_metric_alarm.ec2_cpu.dimensions["InstanceId"] == "i-0123456789abcdef0"
    )
    error_message = "EC2 alarms must be dimensioned by the host instance id."
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.rds_storage.dimensions["DBInstanceIdentifier"] == aws_db_instance.main.identifier &&
      aws_cloudwatch_metric_alarm.rds_cpu_credits.dimensions["DBInstanceIdentifier"] == aws_db_instance.main.identifier
    )
    error_message = "RDS alarms must be dimensioned by the DB identifier."
  }

  assert {
    condition = alltrue([
      for a in [
        aws_cloudwatch_metric_alarm.ec2_status,
        aws_cloudwatch_metric_alarm.ec2_cpu,
        aws_cloudwatch_metric_alarm.rds_storage,
        aws_cloudwatch_metric_alarm.rds_cpu_credits,
      ] : contains(a.ok_actions, aws_sns_topic.alerts.arn)
    ])
    error_message = "Every alarm must announce recovery on the alerts topic."
  }

  assert {
    condition = alltrue([
      for a in [
        aws_cloudwatch_metric_alarm.ec2_cpu,
        aws_cloudwatch_metric_alarm.rds_storage,
        aws_cloudwatch_metric_alarm.rds_cpu_credits,
      ] : !contains(a.alarm_actions, "arn:aws:automate:sa-east-1:ec2:recover") && !contains(a.ok_actions, "arn:aws:automate:sa-east-1:ec2:recover")
    ]) && !contains(aws_cloudwatch_metric_alarm.ec2_status.ok_actions, "arn:aws:automate:sa-east-1:ec2:recover")
    error_message = "ec2:recover must be only on the status alarm, and only as an alarm action."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.ec2_status.treat_missing_data == "notBreaching"
    error_message = "A planned stop or replacement must not fire the status alarm."
  }
}

run "instance_recovers_itself" {
  command = plan

  assert {
    condition     = aws_cloudwatch_metric_alarm.ec2_status.metric_name == "StatusCheckFailed_System" && contains(aws_cloudwatch_metric_alarm.ec2_status.alarm_actions, "arn:aws:automate:sa-east-1:ec2:recover")
    error_message = "The status alarm must trigger ec2:recover."
  }
}

run "budget_ignores_credits" {
  command = plan

  assert {
    condition     = one(aws_budgets_budget.monthly.cost_types).include_credit == false && one(aws_budgets_budget.monthly.cost_types).include_refund == false
    error_message = "The budget must exclude credits and refunds."
  }

  assert {
    condition     = aws_budgets_budget.monthly.limit_amount == "60" && aws_budgets_budget.monthly.limit_unit == "USD" && aws_budgets_budget.monthly.time_unit == "MONTHLY"
    error_message = "The budget must be 60 USD per month by default."
  }

  assert {
    condition = toset([
      for n in aws_budgets_budget.monthly.notification : "${n.notification_type}/${n.threshold}/${n.threshold_type}"
    ]) == toset(["ACTUAL/80/PERCENTAGE", "FORECASTED/100/PERCENTAGE"])
    error_message = "Budget must warn at 80% actual and 100% forecasted."
  }

  assert {
    condition = alltrue([
      for n in aws_budgets_budget.monthly.notification : contains(n.subscriber_email_addresses, "alerts@example.com")
    ])
    error_message = "Budget notices go to alert_email."
  }
}
