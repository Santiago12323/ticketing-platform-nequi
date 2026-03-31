package com.nequi.ticketing_service.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

@Configuration
public class SqsConfig {

    @Value("${aws.access-key:dummy}")
    private String accessKey;

    @Value("${aws.secret-key:dummy}")
    private String secretKey;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${aws.sqs.endpoint:http://localhost:4566}")
    private String sqsEndpoint;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                )
                .endpointOverride(URI.create(sqsEndpoint))
                .build();
    }
}
