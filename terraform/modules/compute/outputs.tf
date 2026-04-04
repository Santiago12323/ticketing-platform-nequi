
output "alb_dns_name" {
  description = "DNS del Load Balancer para acceder a los microservicios"
  value       = aws_lb.main.dns_name
}

output "ecs_cluster_name" {
  description = "Nombre del cluster de ECS"
  value       = aws_ecs_cluster.main.name
}


output "service_target_group_arns" {
  description = "ARNs de los Target Groups indexados por nombre de servicio"
  value       = { for k, v in aws_lb_target_group.services_tg : k => v.arn }
}

output "log_group_names" {
  description = "Nombres de los grupos de logs en CloudWatch para cada servicio"
  value       = { for k, v in aws_cloudwatch_log_group.ecs_logs : k => v.name }
}