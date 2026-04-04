package com.nequi.inventory.infrastructure.persistence.dynamo.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;



@Configuration
@ConfigurationProperties(prefix = "spring.cloud.aws.dynamodb")
@Getter
@Setter
public class DynamoConstants {

    private String idempotencyTable;

    private long idempotencyTtlSeconds;

    private String endpoint;
}