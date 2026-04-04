resource "aws_ecs_cluster" "main" {
  name = "payments-microservices-cluster"
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# --- CLOUDWATCH LOG GROUPS ---
resource "aws_cloudwatch_log_group" "ecs_logs" {
  for_each          = toset(["order", "inventory", "payment", "user"])
  name              = "/ecs/${each.key}-service"
  retention_in_days = 7
}

# --- TARGET GROUPS  ---
resource "aws_lb_target_group" "services_tg" {
  for_each    = toset(["order", "inventory", "payment", "user"])
  name        = "${each.key}-service-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/actuator/health" #
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# --- ALB LISTENER RULES  ---
resource "aws_lb_listener_rule" "routing" {
  for_each     = toset(["order", "inventory", "payment", "user"])
  listener_arn = var.alb_listener_arn
  priority     = index(["order", "inventory", "payment", "user"], each.key) + 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.services_tg[each.key].arn
  }

  condition {
    path_pattern {
      values = ["/api/v1/${each.key}*"]
    }
  }
}


resource "aws_ecs_task_definition" "services_task" {
  for_each                 = toset(["order", "inventory", "payment", "user"])
  family                   = "${each.key}-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = var.ecs_exec_role_arn
  task_role_arn            = var.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "${each.key}-service"
      image     = "${var.repository_url}:${each.key}-latest"
      essential = true
      portMappings = [{ containerPort = 8080, hostPort = 8080 }]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs[each.key].name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "DB_ENDPOINT", value = var.db_endpoint }
      ]

      secrets = [
        { name = "REDIS_PASSWORD", valueFrom = var.redis_password_arn }
      ]
    }
  ])
}

resource "aws_ecs_service" "services" {
  for_each        = toset(["order", "inventory", "payment", "user"])
  name            = "${each.key}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.services_task[each.key].arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = var.private_subnet_ids
    security_groups = [var.ecs_tasks_sg_id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.services_tg[each.key].arn
    container_name   = "${each.key}-service"
    container_port   = 8080
  }
}