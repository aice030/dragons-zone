package com.dragons.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.dragons.core.security.JwtAuthenticationFilter;

/**
 * Spring Security配置类
 * 用于配置Web安全策略
 * 
 * @author aice
 * @since 2026-01-18
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置Spring Security的过滤器链
     * 允许/test/**路径不需要认证，用于测试数据库操作
     * 
     * @param http HttpSecurity对象
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 配置 CORS（必须在其他配置之前）
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 禁用CSRF保护（仅用于测试环境）
            .csrf(AbstractHttpConfigurer::disable)
            // 不使用Session，完全基于JWT
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 关闭默认表单登录/基础认证（我们只用JWT）
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // 配置请求授权
            .authorizeHttpRequests(auth -> auth
                // 允许/test/**路径不需要认证
                .requestMatchers("/test/**").permitAll()
                // 登录、注册、未登录找回密码无需认证
                .requestMatchers("/api/user/login", "/api/user/register", "/api/user/forgotPassword").permitAll()
                // 游客模式：未登录可查看公共区/专区媒体列表、媒体详情、获取下载链接（无需请求头）
                .requestMatchers(HttpMethod.GET, "/api/media/visible/list").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/media/visible/rank").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/media/*/download").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/media/*").permitAll()
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            );

        // JWT过滤器：把 Authorization: Bearer <token> 转换为 Spring Security 的认证信息
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        // 请求日志：在 JWT 之后执行，可记录 userId；每条请求一条 INFO
        http.addFilterAfter(new RequestLoggingFilter(), JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    /**
     * CORS 配置源
     * 允许前端跨域访问
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 允许的源（前端页面 Origin；方案 B 下前端在 80，直连后端 8080 会跨域）：
        // - 本地 Vite(5173)、本机 Docker(8081)
        // - ECS 前端 80：http://<ECS_IP> 或 http://<ECS_IP>:80
        configuration.addAllowedOrigin("http://localhost:5173");
        configuration.addAllowedOrigin("http://localhost:8081");
        configuration.addAllowedOrigin("http://47.118.26.94");
        configuration.addAllowedOrigin("http://47.118.26.94:80");
        
        // 允许的请求头
        configuration.addAllowedHeader("*");
        
        // 允许的请求方法
        configuration.addAllowedMethod("*");
        
        // 允许携带凭证（Cookie、Authorization 等）
        configuration.setAllowCredentials(true);
        
        // 预检请求的缓存时间（秒）
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
