#!/bin/bash

echo "🚀 Iniciando configuración de infraestructura (SQS & SNS)..."

# 1. Crear el SNS Topic
TOPIC_NAME="order-expiration-topic"
awslocal sns create-topic --name $TOPIC_NAME
TOPIC_ARN="arn:aws:sns:us-west-2:000000000000:$TOPIC_NAME"

# 2. Crear Dead Letter Queues (DLQs)
awslocal sqs create-queue --queue-name inventory-request-dlq.fifo --attributes FifoQueue=true
awslocal sqs create-queue --queue-name inventory-response-dlq.fifo --attributes FifoQueue=true

# ARNs de las DLQs
REQ_DLQ_ARN="arn:aws:sqs:us-west-2:000000000000:inventory-request-dlq.fifo"
RES_DLQ_ARN="arn:aws:sqs:us-west-2:000000000000:inventory-response-dlq.fifo"

# 3. Crear Colas de Negocio FIFO
echo "📦 Creando colas principales..."

awslocal sqs create-queue --queue-name inventory-request-queue.fifo \
    --attributes "FifoQueue=true,ContentBasedDeduplication=true,RedrivePolicy='{\"deadLetterTargetArn\":\"$REQ_DLQ_ARN\",\"maxReceiveCount\":\"3\"}'"

awslocal sqs create-queue --queue-name inventory-response-queue.fifo \
    --attributes "FifoQueue=true,ContentBasedDeduplication=true,RedrivePolicy='{\"deadLetterTargetArn\":\"$RES_DLQ_ARN\",\"maxReceiveCount\":\"3\"}'"

# 4. Crear Colas de TTL con Delay
echo "⏳ Configurando colas de TTL (10 min delay)..."
awslocal sqs create-queue --queue-name order-ttl-queue --attributes '{"DelaySeconds": "600"}'
awslocal sqs create-queue --queue-name inventory-ttl-queue --attributes '{"DelaySeconds": "600"}'

# 5. Suscripciones SNS -> SQS
echo "🔗 Suscribiendo colas al tópico..."

awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-west-2:000000000000:order-ttl-queue \
    --attributes '{"RawMessageDelivery": "true"}'

awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-west-2:000000000000:inventory-ttl-queue \
    --attributes '{"RawMessageDelivery": "true"}'

echo "✅ ¡Infraestructura lista!"