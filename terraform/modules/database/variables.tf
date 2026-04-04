variable "private_subnet_ids" {
  type        = list(string)
  description = "IDs de las subnets privadas donde vivirá Redis"
}

variable "redis_sg_id" {
  type        = string
  description = "Security Group que permite tráfico al puerto 6379"
}

variable "redis_password" {
  type        = string
  sensitive   = true
  description = "Password de Redis almacenado en AWS Secrets Manager"
}