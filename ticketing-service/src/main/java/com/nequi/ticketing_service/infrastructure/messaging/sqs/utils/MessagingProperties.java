package com.nequi.ticketing_service.infrastructure.messaging.sqs.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    private Sqs sqs;
    private Retry retry;
    private Audit audit;

    @Getter
    @Setter
    public static class Sqs {
        private String inventoryQueueUrl;
        private String messageGroupId;
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts;
        private long backoffMillis;
    }

    @Getter
    @Setter
    public static class Audit {
        private boolean enabled;
    }
}