locals {
  # Each alarm watches one metric and emails both when it fires and when it clears. CloudWatch's
  # free tier covers ten alarms and ten custom metrics; this module uses seven alarms and the
  # backend's three custom metrics. Thresholds are first guesses, tuned once real traffic shows what
  # normal looks like.
  alarms = {
    backend-not-in-service = {
      description = "No backend instance in service: the API is down."
      namespace   = "AWS/AutoScaling"
      metric      = "GroupInServiceInstances"
      dimensions  = { AutoScalingGroupName = var.autoscaling_group_name }
      statistic   = "Minimum"
      comparison  = "LessThanThreshold"
      threshold   = 1
      # Twenty minutes: a deploy or a spot replacement leaves the group empty for a few.
      periods = 4
      # No data means the group itself is gone or stopped reporting.
      missing_data = "breaching"
    }
    backend-memory = {
      description = "The backend instance has used more than 90% of its memory for 15 minutes."
      namespace   = var.backend_metrics_namespace
      metric      = "mem_used_percent"
      dimensions  = {}
      statistic   = "Maximum"
      comparison  = "GreaterThanThreshold"
      threshold   = 90
      periods     = 3
      # No data shows as INSUFFICIENT_DATA, not OK: an agent that stopped, or dimensions that do
      # not match what it publishes, are seen instead of silencing the alarm.
      missing_data = "missing"
    }
    backend-disk = {
      description = "The backend instance's root volume is more than 80% full."
      namespace   = var.backend_metrics_namespace
      metric      = "disk_used_percent"
      # The agent's aggregation by path alone, independent of the file system's type.
      dimensions   = { path = "/" }
      statistic    = "Maximum"
      comparison   = "GreaterThanThreshold"
      threshold    = 80
      periods      = 1
      missing_data = "missing"
    }
    cloudfront-5xx = {
      description = "More than 5% of CloudFront's responses have been server errors for 15 minutes."
      namespace   = "AWS/CloudFront"
      metric      = "5xxErrorRate"
      # CloudFront's metrics live in us-east-1, under the Global region.
      dimensions   = { DistributionId = var.distribution_id, Region = "Global" }
      statistic    = "Average"
      comparison   = "GreaterThanThreshold"
      threshold    = 5
      periods      = 3
      missing_data = "notBreaching"
    }
    database-cpu = {
      description  = "The database's CPU has been above 80% for 15 minutes."
      namespace    = "AWS/RDS"
      metric       = "CPUUtilization"
      dimensions   = { DBInstanceIdentifier = var.database_identifier }
      statistic    = "Average"
      comparison   = "GreaterThanThreshold"
      threshold    = 80
      periods      = 3
      missing_data = "notBreaching"
    }
    database-cpu-surplus-charged = {
      # RDS runs burstable classes in unlimited mode: once the credits run out, the CPU above the
      # baseline is billed instead of throttled. A new instance starts with no credits, so the
      # balance itself would alarm on every first apply; the charge is what matters.
      description  = "The database is being billed for CPU above its baseline (unlimited mode)."
      namespace    = "AWS/RDS"
      metric       = "CPUSurplusCreditsCharged"
      dimensions   = { DBInstanceIdentifier = var.database_identifier }
      statistic    = "Sum"
      comparison   = "GreaterThanThreshold"
      threshold    = 0
      periods      = 1
      missing_data = "notBreaching"
    }
    database-storage = {
      description  = "The database has less than 2 GB of free storage."
      namespace    = "AWS/RDS"
      metric       = "FreeStorageSpace"
      dimensions   = { DBInstanceIdentifier = var.database_identifier }
      statistic    = "Minimum"
      comparison   = "LessThanThreshold"
      threshold    = 2 * 1024 * 1024 * 1024
      periods      = 1
      missing_data = "notBreaching"
    }
  }

  # Every alarm's metric is averaged over five minutes.
  period = 300

  region = data.aws_region.current.region
}

data "aws_region" "current" {}

# --- Where alarms go ---

# Encrypting the topic would take a customer-managed key: CloudWatch cannot publish to one
# encrypted with SNS's own key, and a customer-managed key bills monthly. Alarms carry no secrets.
#trivy:ignore:AVD-AWS-0095 CloudWatch needs a customer-managed key to publish, which bills monthly
resource "aws_sns_topic" "alarms" {
  name = "${var.name}-alarms"
}

# Pending until the address follows the link AWS emails it. Terraform cannot confirm it.
resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# --- Alarms ---

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each = local.alarms

  alarm_name          = "${var.name}-${each.key}"
  alarm_description   = each.value.description
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  dimensions          = each.value.dimensions
  statistic           = each.value.statistic
  comparison_operator = each.value.comparison
  threshold           = each.value.threshold
  period              = local.period
  evaluation_periods  = each.value.periods
  treat_missing_data  = each.value.missing_data

  alarm_actions = [aws_sns_topic.alarms.arn]
  ok_actions    = [aws_sns_topic.alarms.arn]

  lifecycle {
    # CloudFront publishes its metrics only in us-east-1, where an alarm on them must live.
    precondition {
      condition     = local.region == "us-east-1"
      error_message = "CloudFront's metrics are only in us-east-1; apply this stack there."
    }
  }
}

# --- Dashboard ---

# One graph per alarm, with its threshold drawn in, three to a row. The first three dashboards
# are free.
resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = var.name

  dashboard_body = jsonencode({
    widgets = [
      for index, key in keys(local.alarms) : {
        type   = "metric"
        x      = (index % 3) * 8
        y      = floor(index / 3) * 6
        width  = 8
        height = 6
        properties = {
          title  = local.alarms[key].description
          region = local.region
          stat   = local.alarms[key].statistic
          period = local.period
          metrics = [concat(
            [local.alarms[key].namespace, local.alarms[key].metric],
            flatten([for dimension, value in local.alarms[key].dimensions : [dimension, value]]),
          )]
          annotations = {
            horizontal = [{ label = "Alarm", value = local.alarms[key].threshold }]
          }
        }
      }
    ]
  })
}
