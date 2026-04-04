output "ecs_exec_role_arn" { value = aws_iam_role.ecs_exec_role.arn }
output "ecs_task_role_arn" { value = aws_iam_role.ecs_task_role.arn }
output "waf_acl_arn" { value = aws_wafv2_web_acl.main.arn }
output "cognito_user_pool_id" { value = aws_cognito_user_pool.users.id }
output "redis_secret_arn" { value = aws_secretsmanager_secret.redis_password.arn }