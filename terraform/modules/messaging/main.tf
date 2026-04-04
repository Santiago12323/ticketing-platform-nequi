# ==========================================================
# 1. COLAS FIFO
# ==========================================================

resource "aws_sqs_queue" "inventory_request_dlq" {
  name       = "inventory-request-dlq.fifo"
  fifo_queue = true
}

resource "aws_sqs_queue" "inventory_request" {
  name                        = "inventory-request-queue.fifo"
  fifo_queue                  = true
  content_based_deduplication = true
  visibility_timeout_seconds  = 30
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.inventory_request_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "inventory_response_dlq" {
  name       = "inventory-response-dlq.fifo"
  fifo_queue = true
}

resource "aws_sqs_queue" "inventory_response" {
  name                        = "inventory-response-queue.fifo"
  fifo_queue                  = true
  content_based_deduplication = true
  visibility_timeout_seconds  = 30
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.inventory_response_dlq.arn
    maxReceiveCount     = 3
  })
}

# ==========================================================
# 2. COLAS DE EXPIRACIÓN (TTL - 10 Minutos)
# ==========================================================

resource "aws_sqs_queue" "order_ttl" {
  name          = "order-ttl-queue"
  delay_seconds = 600 # 10 minutos exactos
}

resource "aws_sqs_queue" "inventory_ttl" {
  name          = "inventory-ttl-queue"
  delay_seconds = 600
}

# ==========================================================
# 3. SNS TOPICS
# ==========================================================

resource "aws_sns_topic" "order_expiration" {
  name              = "order-expiration-topic"
  kms_master_key_id = "alias/aws/sns"
}

resource "aws_sns_topic" "order_status_changed" {
  name              = "order-status-changed-topic"
  kms_master_key_id = "alias/aws/sns"
}

# ==========================================================
# 4. SUSCRIPCIONES Y FILTROS
# ==========================================================

# Suscripción SQS -> SNS
resource "aws_sns_topic_subscription" "order_ttl_sub" {
  topic_arn = aws_sns_topic.order_expiration.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.order_ttl.arn
}

resource "aws_sns_topic_subscription" "inventory_ttl_sub" {
  topic_arn = aws_sns_topic.order_expiration.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.inventory_ttl.arn
}

# ==========================================================
# 5. LAMBDA DE NOTIFICACIONES
# ==========================================================

resource "aws_lambda_function" "notification_service" {
  filename      = "${path.root}/scripts/notification_handler.zip"
  function_name = "OrderNotificationService"
  role          = var.lambda_execution_role_arn
  handler       = "index.handler"
  runtime       = "nodejs18.x"

  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [var.lambda_sg_id]
  }

  environment {
    variables = {
      ENVIRONMENT = "PROD"
      APP_NAME    = "TicketingService"
    }
  }
}

resource "aws_lambda_permission" "sns_lambda_allow" {
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.notification_service.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.order_status_changed.arn
}

# Suscripción Lambda -> SNS con Filtro de Estado
resource "aws_sns_topic_subscription" "lambda_notif_sub" {
  topic_arn = aws_sns_topic.order_status_changed.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.notification_service.arn

  filter_policy = jsonencode({
    status = ["COMPLETED", "FAILED", "EXPIRED"]
  })
}