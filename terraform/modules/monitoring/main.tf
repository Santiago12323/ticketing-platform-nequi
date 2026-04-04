# --- CLOUDWATCH DASHBOARD CENTRALIZADO ---
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "Microservices-Health-Dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            [ "AWS/ECS", "CPUUtilization", "ClusterName", var.ecs_cluster_name ],
            [ ".", "MemoryUtilization", ".", "." ]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "Cluster CPU & Memory"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            [ "AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", var.alb_arn_suffix ]
          ]
          period = 60
          stat   = "Sum"
          region = var.aws_region
          title  = "Errores 5XX (Critical)"
        }
      }
    ]
  })
}


resource "aws_cloudwatch_metric_alarm" "dlq_alarm" {
  alarm_name          = "sqs-inventory-dlq-not-empty"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = "60"
  statistic           = "Sum"
  threshold           = "0"
  alarm_description   = "Esta alarma se dispara si hay mensajes fallidos en la DLQ de Inventario"

  dimensions = {
    QueueName = "inventory-request-dlq.fifo"
  }

  alarm_actions = [var.sns_topic_admin_alerts_arn]
}

resource "aws_cloudwatch_log_metric_filter" "waf_blocked_requests" {
  name           = "WAFBlockedRequests"
  pattern        = "{ $.action = \"BLOCK\" }"
  log_group_name = var.waf_log_group_name

  metric_transformation {
    name      = "BlockedRequestCount"
    namespace = "WAFMetrics"
    value     = "1"
  }
}