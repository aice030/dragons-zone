package com.dragons.core.mq;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 仅当 media.like.use-mq=true 时启用 RocketMQ 自动配置（连接 name-server、创建 Producer 等）。
 * 配合 application.yml 中 spring.autoconfigure.exclude 默认排除 RocketMQ，实现「不启用 MQ 时无需启动 RocketMQ 即可启动应用」。
 *
 * @author aice
 * @since 2026-02-24
 */
@Configuration
@ConditionalOnProperty(name = "media.like.use-mq", havingValue = "true")
@Import(RocketMQAutoConfiguration.class)
public class RocketMQEnableConfig {
}
