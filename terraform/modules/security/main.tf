provider "aws" {
  region = "us-west-2"
}

# --- 1. AWS WAF ---
resource "aws_wafv2_web_acl" "main" {
  name        = "microservices-waf"
  description = "Proteccion para el ALB de pagos"
  scope       = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "AWSManagedRulesAmazonIpReputationList"
    priority = 1

    override_action { none {} }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesAmazonIpReputationList"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "WAF_IP_Reputation"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "main-waf"
    sampled_requests_enabled   = true
  }
}

# --- 2. COGNITO ---
resource "aws_cognito_user_pool" "users" {
  name = "nequi-ticketing-user-pool"

  password_policy {
    minimum_length    = 12
    require_lowercase = true
    require_numbers   = true
    require_symbols   = true
    require_uppercase = true
  }

  schema {
    attribute_data_type = "String"
    name                = "email"
    required            = true
    mutable             = false
  }

  auto_verified_attributes = ["email"]
}

resource "aws_cognito_user_pool_client" "client" {
  name         = "ticketing-app-client"
  user_pool_id = aws_cognito_user_pool.users.id

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]
}

# --- 3. IAM ROLES ---

resource "aws_iam_role" "ecs_exec_role" {
  name = "ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_exec_standard" {
  role       = aws_iam_role.ecs_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "ecs_task_role" {
  name = "ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

# Permisos microservicios
resource "aws_iam_role_policy" "microservices_policy" {
  name = "microservices-access-policy"
  role = aws_iam_role.ecs_task_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:PutItem", "dynamodb:GetItem", "dynamodb:UpdateItem", "dynamodb:Query"]
        Resource = "*"
      }
    ]
  })
}


resource "aws_secretsmanager_secret" "app_secrets" {
  name        = "prod/ticketing-service"
  description = "Secrets para microservicio ticketing"
}

resource "aws_secretsmanager_secret_version" "app_secrets_base" {
  secret_id = aws_secretsmanager_secret.app_secrets.id

  secret_string = jsonencode({
    REDIS_HOST = "my-redis.xxxxxx.cache.amazonaws.com"
    REDIS_PORT = "6379"

    INVENTORY_REQUEST_QUEUE_URL  = "https://sqs.us-west-2.amazonaws.com/xxx/inventory-request"
    INVENTORY_RESPONSE_QUEUE_URL = "https://sqs.us-west-2.amazonaws.com/xxx/inventory-response"
    INVENTORY_REQUEST_DLQ_URL    = "https://sqs.us-west-2.amazonaws.com/xxx/inventory-dlq"

    ORDER_TTL_QUEUE_URL     = "https://sqs.us-west-2.amazonaws.com/xxx/order-ttl"
    INVENTORY_TTL_QUEUE_URL = "https://sqs.us-west-2.amazonaws.com/xxx/inventory-ttl"

    ORDER_EXPIRATION_TOPIC_ARN = "arn:aws:sns:us-west-2:xxx:order-expiration-topic"

    IDEMPOTENCY_TABLE = "Idempotency"
  })
}

resource "aws_iam_role_policy" "secrets_access" {
  name = "secrets-access-policy"
  role = aws_iam_role.ecs_task_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = aws_secretsmanager_secret.app_secrets.arn
      }
    ]
  })
}

