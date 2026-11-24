locals {
  # The run-missing alarm is production only: staging may sit idle and would false-alarm.
  indicator_run_log_groups = {
    for env, group in local.api_log_groups : env => group if env == "production"
  }
}

resource "aws_cloudwatch_log_metric_filter" "indicator_alert" {
  for_each = local.api_log_groups

  name = "liberdade-indicator-alert-${each.key}"
  # Quoted term: matches the "INDICATOR_ALERT type=<TYPE> series=<CODE>" WARN line the API writes.
  pattern        = "\"INDICATOR_ALERT\""
  log_group_name = aws_cloudwatch_log_group.main[each.key].name

  metric_transformation {
    name      = "IndicatorAlerts"
    namespace = "Liberdade/${each.key}"
    value     = "1"
    # Emit 0 when no alert is logged, so the alarm returns to OK on its own.
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "indicator_alert" {
  for_each = local.api_log_groups

  alarm_name          = "liberdade-indicator-alert-${each.key}"
  alarm_description   = "The ${each.key} API logged an indicator alert; check the logs for the series and the failure kind."
  namespace           = "Liberdade/${each.key}"
  metric_name         = "IndicatorAlerts"
  statistic           = "Sum"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  period              = 300
  evaluation_periods  = 1
  # No log lines means no alert, so a quiet period stays OK.
  treat_missing_data = "notBreaching"
  alarm_actions      = [aws_sns_topic.alerts.arn]
  ok_actions         = [aws_sns_topic.alerts.arn]

  depends_on = [aws_cloudwatch_log_metric_filter.indicator_alert]
}

resource "aws_cloudwatch_log_metric_filter" "indicator_run" {
  for_each = local.indicator_run_log_groups

  name = "liberdade-indicator-run-${each.key}"
  # Quoted term: matches the "INDICATOR_RUN ok=<n> failed=<n>" INFO line written after every run.
  pattern        = "\"INDICATOR_RUN\""
  log_group_name = aws_cloudwatch_log_group.main[each.key].name

  # No default_value on purpose: a missing run must show up as missing data.
  metric_transformation {
    name      = "IndicatorRuns"
    namespace = "Liberdade/${each.key}"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "indicator_run_missing" {
  for_each = local.indicator_run_log_groups

  alarm_name          = "liberdade-indicator-run-missing-${each.key}"
  alarm_description   = "The ${each.key} API logged no indicator run for 24 hours; check the scheduler and the logs."
  namespace           = "Liberdade/${each.key}"
  metric_name         = "IndicatorRuns"
  statistic           = "Sum"
  comparison_operator = "LessThanThreshold"
  threshold           = 1
  period              = 3600
  evaluation_periods  = 24
  # CloudWatch caps the evaluation window at one day; runs are at most 13 hours apart.
  treat_missing_data = "breaching"
  alarm_actions      = [aws_sns_topic.alerts.arn]
  ok_actions         = [aws_sns_topic.alerts.arn]

  depends_on = [aws_cloudwatch_log_metric_filter.indicator_run]
}
