package com.dragons.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置
 * 
 * 允许前端（http://localhost:5173）访问后端 API
 * 
 * @author aice
 * @since 2026-02-11
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的源（前端页面 Origin，方案 B 下前端直连后端 8080 会跨域）：
        // - 本地开发 Vite(5173)、本机 Docker(8081)
        // - ECS 前端端口 80：http://<ECS_IP> 或 http://<ECS_IP>:80
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedOrigin("http://localhost:8081");
        config.addAllowedOrigin("http://47.118.26.94");
        config.addAllowedOrigin("http://47.118.26.94:80");
        
        // 允许的请求头
        config.addAllowedHeader("*");
        
        // 允许的请求方法
        config.addAllowedMethod("*");
        
        // 允许携带凭证（Cookie、Authorization 等）
        config.setAllowCredentials(true);
        
        // 预检请求的缓存时间（秒）
        config.setMaxAge(3600L);
        
        // 对所有路径生效
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
