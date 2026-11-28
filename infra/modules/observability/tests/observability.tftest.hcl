# Runs against a mocked AWS provider: no credentials, no account, nothing created.

# Fixed partition, region and account, so the ARNs the module builds are valid.
mock_provider "aws" {
  source = "../../testing/aws"
}

variables {
  name                      = "itacatl-test"
  alarm_email               = "alerts@example.com"
  autoscaling_group_name    = "itacatl-test-backend"
  backend_metrics_namespace = "itacatl-test/backend"
  database_identifier       = "itacatl-test"
  distribution_id           = "E2ABCDEFGHIJKL"
}

run "it_stays_within_cloudwatchs_free_tier" {
  command = plan

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.this) <= 10
    error_message = "CloudWatch's free tier covers ten alarms; each one more bills monthly."
  }

  assert {
    condition = alltrue([
      for alarm in aws_cloudwatch_metric_alarm.this : alarm.period >= 60
    ])
    error_message = "High-resolution alarms (under a minute) bill more and have no free tier."
  }
}

run "each_part_of_the_stack_is_watched" {
  command = plan

  assert {
    condition = alltrue([
      for pair in [
        ["AWS/AutoScaling", "GroupInServiceInstances"],
        ["itacatl-test/backend", "mem_used_percent"],
        ["itacatl-test/backend", "disk_used_percent"],
        ["AWS/CloudFront", "5xxErrorRate"],
        ["AWS/RDS", "CPUUtilization"],
        ["AWS/RDS", "CPUSurplusCreditsCharged"],
        ["AWS/RDS", "FreeStorageSpace"],
        ] : anytrue([
          for alarm in aws_cloudwatch_metric_alarm.this :
          alarm.namespace == pair[0] && alarm.metric_name == pair[1]
      ])
    ])
    error_message = "The backend's group, memory and disk, CloudFront's errors and the database's CPU, billed surplus CPU and storage must each have an alarm."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.this["backend-not-in-service"].dimensions.AutoScalingGroupName == var.autoscaling_group_name
    error_message = "The empty-group alarm must watch the backend's group."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.this["backend-not-in-service"].treat_missing_data == "breaching"
    error_message = "A group that stops reporting must count as one with nothing in service."
  }

  assert {
    condition = alltrue([
      for key in ["backend-memory", "backend-disk"] :
      aws_cloudwatch_metric_alarm.this[key].treat_missing_data == "missing"
    ])
    error_message = "An agent that stops publishing must show as missing data, never as OK."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.this["cloudfront-5xx"].dimensions == tomap({ DistributionId = var.distribution_id, Region = "Global" })
    error_message = "The 5xx alarm must watch the distribution's global metrics."
  }
}

# The mock leaves the topic's ARN unknown in a plan.
run "alarms_email_when_they_fire_and_when_they_clear" {
  command = apply

  assert {
    condition = alltrue([
      for alarm in aws_cloudwatch_metric_alarm.this :
      alarm.alarm_actions == toset([aws_sns_topic.alarms.arn]) && alarm.ok_actions == toset([aws_sns_topic.alarms.arn])
    ])
    error_message = "Every alarm must notify the topic both when it fires and when it clears."
  }

  assert {
    condition = (
      aws_sns_topic_subscription.email.protocol == "email" &&
      aws_sns_topic_subscription.email.endpoint == var.alarm_email
    )
    error_message = "The topic must email the alarm address."
  }
}

run "the_dashboard_graphs_every_alarm" {
  command = plan

  assert {
    condition     = length(jsondecode(aws_cloudwatch_dashboard.this.dashboard_body).widgets) == length(aws_cloudwatch_metric_alarm.this)
    error_message = "The dashboard must have one graph per alarm."
  }
}

run "an_invalid_email_is_rejected" {
  command = plan

  variables {
    alarm_email = "not-an-email"
  }

  expect_failures = [var.alarm_email]
}

run "another_region_is_rejected" {
  command = plan

  override_data {
    target = data.aws_region.current
    values = { region = "eu-west-1" }
  }

  expect_failures = [aws_cloudwatch_metric_alarm.this]
}
