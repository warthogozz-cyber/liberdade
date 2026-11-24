locals {
  # Only the API logs billing and indicator alerts; the caddy group is left out.
  api_log_groups = {
    staging    = "/liberdade/staging"
    production = "/liberdade/production"
  }
}

resource "aws_cloudwatch_log_metric_filter" "billing_alert" {
  for_each = local.api_log_groups

  name = "liberdade-billing-alert-${each.key}"
  # Quoted term: matches the "BILLING_ALERT type=<TYPE>" WARN line the API writes.
  pattern        = "\"BILLING_ALERT\""
  log_group_name = aws_cloudwatch_log_group.main[each.key].name

  metric_transformation {
    name      = "BillingAlerts"
    namespace = "Liberdade/${each.key}"
    value     = "1"
    # Emit 0 when no alert is logged, so the alarm returns to OK on its own.
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "billing_alert" {
  for_each = local.api_log_groups

  alarm_name          = "liberdade-billing-alert-${each.key}"
  alarm_description   = "The ${each.key} API logged a billing alert; check the logs for the alert type."
  namespace           = "Liberdade/${each.key}"
  metric_name         = "BillingAlerts"
  statistic           = "Sum"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  period              = 300
  evaluation_periods  = 1
  # No log lines means no alert, so a quiet period stays OK.
  treat_missing_data = "notBreaching"
  alarm_actions      = [aws_sns_topic.alerts.arn]
  ok_actions         = [aws_sns_topic.alerts.arn]

  depends_on = [aws_cloudwatch_log_metric_filter.billing_alert]
}
