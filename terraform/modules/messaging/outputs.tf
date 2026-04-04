output "order_expiration_topic_arn" {
  value = aws_sns_topic.order_expiration.arn
}

output "inventory_ttl_queue_url" {
  value = aws_sqs_queue.inventory_ttl.id
}