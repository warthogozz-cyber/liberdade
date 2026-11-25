resource "aws_cloudwatch_log_metric_filter" "markets_alert" {
  for_each = local.api_log_groups

  name = "liberdade-markets-alert-${each.key}"
  # Quoted term: matches the "MARKETS_ALERT type=<TYPE> ..." WARN line the API writes when the weekly import fails or the data is stale.
  pattern        = "\"MARKETS_ALERT\""
  log_group_name = aws_cloudwatch_log_group.main[each.key].name

  metric_transformation {
    name      = "MarketsAlerts"
    namespace = "Liberdade/${each.key}"
    value     = "1"
    # Emit 0 when no alert is logged, so the alarm returns to OK on its own.
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "markets_alert" {
  for_each = local.api_log_groups

  alarm_name          = "liberdade-markets-alert-${each.key}"
  alarm_description   = "The ${each.key} API logged a markets alert; check the logs for the failure reason or the stale data."
  namespace           = "Liberdade/${each.key}"
  metric_name         = "MarketsAlerts"
  statistic           = "Sum"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  # The import runs weekly and the stale check daily, so an hour is fine grained enough.
  period             = 3600
  evaluation_periods = 1
  # No log lines means no alert, so a quiet period stays OK.
  treat_missing_data = "notBreaching"
  alarm_actions      = [aws_sns_topic.alerts.arn]
  ok_actions         = [aws_sns_topic.alerts.arn]

  depends_on = [aws_cloudwatch_log_metric_filter.markets_alert]
}
