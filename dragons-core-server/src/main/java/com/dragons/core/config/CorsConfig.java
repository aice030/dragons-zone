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
        
        // 允许的源（前端地址）：
        // - 本地开发 Vite(5173)
        // - 本机 Docker Nginx(8081)
        // - 云服务器前端：39.105.137.42:8081
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedOrigin("http://localhost:8081");
        config.addAllowedOrigin("http://39.105.137.42:8081");
        
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
