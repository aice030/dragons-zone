package com.dragons.core.retry;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DB 写入重试的全局配置。
 *
 * 配置来源：
 * application.yml / application-example.yml 中的 db-retry.*。
 */
@Component
@ConfigurationProperties(prefix = "db-retry")
@Getter
@Setter
public class DbRetryProperties {

    /** 最大尝试次数（包含首次）。 */
    private int maxAttempts = 3;

    /** 首次重试延迟（毫秒）。 */
    private long initialDelayMs = 100L;

    /** 最大延迟（毫秒）。 */
    private long maxDelayMs = 1000L;

    /** 指数退避倍率。 */
    private double multiplier = 2.0d;

    /** 抖动比例（0-1）。 */
    private double jitterRatio = 0.2d;
}
