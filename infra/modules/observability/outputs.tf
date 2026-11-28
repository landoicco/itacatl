output "alarm_topic_arn" {
  description = "SNS topic every alarm notifies, when it fires and when it clears."
  value       = aws_sns_topic.alarms.arn
}

output "alarms" {
  description = "Each CloudWatch alarm by name, with the namespace and dimensions of the metric it watches."
  value = {
    for alarm in aws_cloudwatch_metric_alarm.this : alarm.alarm_name => {
      namespace  = alarm.namespace
      dimensions = alarm.dimensions
    }
  }
}

output "dashboard_name" {
  description = "Name of the CloudWatch dashboard."
  value       = aws_cloudwatch_dashboard.this.dashboard_name
}
