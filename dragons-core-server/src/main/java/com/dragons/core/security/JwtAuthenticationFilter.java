package com.dragons.core.security;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器（最小实现）
 *
 * 作用：
 * - 读取请求头 Authorization: Bearer <token>
 * - 校验token有效性
 * - 将用户信息放入 Spring Security 的 SecurityContext
 *
 * @author aice
 * @since 2026-01-21
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        // 登录/注册放行（无需token）
        if ("/api/user/login".equals(path) || "/api/user/register".equals(path) || path.startsWith("/test/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        String token = (authorization != null && authorization.startsWith("Bearer "))
                ? authorization.substring(7)
                : null;

        // 没有token：交给后续的Spring Security规则处理（会返回401/403）
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // token无效：直接返回401（统一Result格式）
        if (!jwtUtil.validateToken(token)) {
            writeUnauthorized(response, ResponseCode.TOKEN_INVALID);
            return;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String loginName = jwtUtil.getLoginNameFromToken(token);

        if (userId == null || loginName == null) {
            writeUnauthorized(response, ResponseCode.TOKEN_INVALID);
            return;
        }

        // 已经有认证信息则不重复设置
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing == null) {
            JwtPrincipal principal = new JwtPrincipal(userId, loginName);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, ResponseCode code) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> body = Result.error(code);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

