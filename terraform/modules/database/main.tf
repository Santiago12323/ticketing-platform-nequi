
resource "aws_dynamodb_table" "inventory_event" {
  name           = "Event"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "eventId"

  attribute { name = "eventId"; type = "S" }

  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }

  tags = { Service = "Inventory" }
}

resource "aws_dynamodb_table" "inventory_ticket" {
  name           = "Ticket"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "eventId"
  range_key      = "ticketId"

  attribute { name = "eventId"; type = "S" }
  attribute { name = "ticketId"; type = "S" }

  global_secondary_index {
    name               = "ticketId-index"
    hash_key           = "ticketId"
    projection_type    = "ALL"
  }

  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }

  tags = { Service = "Inventory" }
}

resource "aws_dynamodb_table" "ticket_history" {
  name           = "TicketHistory"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "ticketId"
  range_key      = "timestamp"

  attribute { name = "ticketId"; type = "S" }
  attribute { name = "timestamp"; type = "N" }

  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }

  tags = {
    Service = "Inventory"
    Layer   = "Audit"
  }
}

resource "aws_dynamodb_table" "order_history" {
  name           = "OrderHistory"
  billing_mode   = "PAY_PER_REQUEST"

  hash_key       = "orderId"
  range_key      = "timestamp"

  attribute { name = "orderId"; type = "S" }
  attribute { name = "timestamp"; type = "N" }

  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }

  tags = {
    Service = "Order"
    Layer   = "Audit"
  }
}

resource "aws_dynamodb_table" "orders" {
  name           = "Orders"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "orderId"

  attribute { name = "orderId"; type = "S" }

  stream_enabled   = true
  stream_view_type = "NEW_AND_OLD_IMAGES"

  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }

  tags = { Service = "Order" }
}

resource "aws_dynamodb_table" "idempotency" {
  name           = "Idempotency"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "idempotencyKey"

  attribute { name = "idempotencyKey"; type = "S" }

  ttl {
    attribute_name = "expirationTime"
    enabled        = true
  }

  tags = { Layer = "Common" }
}

resource "aws_elasticache_subnet_group" "redis_subnets" {
  name       = "redis-privadas"
  subnet_ids = var.private_subnet_ids
}

resource "aws_elasticache_replication_group" "redis_cluster" {
  replication_group_id          = "microservices-cache"
  description                   = "Cache para estados de sesion y transacciones"
  node_type                     = "cache.t4g.small"
  port                          = 6379
  parameter_group_name          = "default.redis7"
  subnet_group_name             = aws_elasticache_subnet_group.redis_subnets.name
  security_group_ids            = [var.redis_sg_id]

  automatic_failover_enabled    = true
  multi_az_enabled              = true
  at_rest_encryption_enabled    = true
  transit_encryption_enabled    = true

  auth_token                    = var.redis_password
}

resource "aws_dynamodb_table" "users" {
  name           = "User"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "userId"

  attribute { name = "userId"; type = "S" }
  attribute { name = "email"; type = "S" }

  global_secondary_index {
    name               = "email-index"
    hash_key           = "email"
    projection_type    = "ALL"
  }

  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }

  tags = { Service = "User" }
}

resource "aws_dynamodb_table" "payments" {
  name           = "Payment"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "paymentId"
  range_key      = "orderId"

  attribute { name = "paymentId"; type = "S" }
  attribute { name = "orderId"; type = "S" }

  stream_enabled   = true
  stream_view_type = "NEW_AND_OLD_IMAGES"

  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }

  tags = { Service = "Payment" }
}

resource "aws_dynamodb_table" "payment_history" {
  name           = "PaymentHistory"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "transactionId"

  attribute { name = "transactionId"; type = "S" }

  tags = { Service = "Payment", Layer = "Audit" }
}