#!/bin/bash

echo "🚀 Iniciando configuración de infraestructura (SQS & SNS)..."

# 1. Crear el SNS Topic para Expiración (TTL)
awslocal sns create-topic --name order-expiration-topic
TOPIC_ARN="arn:aws:sns:us-west-2:000000000000:order-expiration-topic"

# 2. Crear Dead Letter Queues (DLQs)
awslocal sqs create-queue --queue-name inventory-request-dlq.fifo --attributes FifoQueue=true
awslocal sqs create-queue --queue-name inventory-response-dlq.fifo --attributes FifoQueue=true

# ARNs de las DLQs
REQ_DLQ_ARN="arn:aws:sqs:us-west-2:000000000000:inventory-request-dlq.fifo"
RES_DLQ_ARN="arn:aws:sqs:us-west-2:000000000000:inventory-response-dlq.fifo"

# 3. Crear Colas de Negocio FIFO con Redrive Policy
awslocal sqs create-queue --queue-name inventory-request-queue.fifo \
    --attributes "FifoQueue=true,ContentBasedDeduplication=true,RedrivePolicy='{\"deadLetterTargetArn\":\"$REQ_DLQ_ARN\",\"maxReceiveCount\":\"3\"}'"

awslocal sqs create-queue --queue-name inventory-response-queue.fifo \
    --attributes "FifoQueue=true,ContentBasedDeduplication=true,RedrivePolicy='{\"deadLetterTargetArn\":\"$RES_DLQ_ARN\",\"maxReceiveCount\":\"3\"}'"

# 4. Crear Colas de TTL con RETRASO DE 10 MINUTOS (600 segundos)
# El atributo DelaySeconds hace que el mensaje sea invisible por ese tiempo.
echo "⏳ Configurando colas de TTL con 10 minutos de delay..."
awslocal sqs create-queue --queue-name order-ttl-queue \
    --attributes '{"DelaySeconds": "600"}'

awslocal sqs create-queue --queue-name inventory-ttl-queue \
    --attributes '{"DelaySeconds": "600"}'

# 5. Configurar el Fan-out: Suscribir colas al Topic
# Usamos --attributes '{"RawMessageDelivery": "true"}' para que a Spring le llegue el JSON
# exactamente como lo mandaste, sin el meta-data de SNS.
echo "🔗 Suscribiendo colas al tópico de expiración..."

awslocal sns subscribe \
    --topic-arn "$TOPIC_ARN" \
    --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-west-2:000000000000:order-ttl-queue \
    --attributes '{"RawMessageDelivery": "true"}'

awslocal sns subscribe \
    --topic-arn "$TOPIC_ARN" \
    --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-west-2:000000000000:inventory-ttl-queue \
    --attributes '{"RawMessageDelivery": "true"}'

echo "------------------------------------------------------"
echo "✅ Infraestructura lista: 1 Topic, 4 Queues, 2 DLQs."
echo "⏱️ Delay de 10 min activo en colas *-ttl-queue."
echo "------------------------------------------------------"