package com.github.codeby5to.sqs.listener.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Configuration
@ConfigurationProperties(prefix = "entrypoint.sqs")
public class SQSProperties {
    private String region;
    private String queueUrl;
    private String endpoint;
    private int waitTimeSeconds;
    private int maxNumberOfMessages;
    private int visibilityTimeoutSeconds;
    private int numberOfThreads;
}
