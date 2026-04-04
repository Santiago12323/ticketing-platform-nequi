output "order_table_arn" {
  value = aws_dynamodb_table.orders.arn
}

output "redis_endpoint" {
  value = aws_elasticache_replication_group.redis_cluster.primary_endpoint_address
}