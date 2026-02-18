package com.dragons.core.config;

import com.dragons.core.security.JwtPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求级汇总日志：在 filterChain 执行后记录 method、path、status、耗时、userId（若已认证）。
 * 不记录 body、query、token 等敏感信息。
 *
 * @author aice
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long startMs = System.currentTimeMillis();
        StatusCapturingResponseWrapper wrappedResponse = new StatusCapturingResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            int status = wrappedResponse.getCapturedStatus();
            long durationMs = System.currentTimeMillis() - startMs;
            String method = request.getMethod();
            String path = request.getRequestURI();
            Long userId = null;
            try {
                Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                        ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                        : null;
                if (principal instanceof JwtPrincipal) {
                    userId = ((JwtPrincipal) principal).getUserId();
                }
            } catch (Exception ignored) {
                // ignore
            }
            if (userId != null) {
                log.info("request finished method={} path={} status={} durationMs={} userId={}",
                        method, path, status, durationMs, userId);
            } else {
                log.info("request finished method={} path={} status={} durationMs={}",
                        method, path, status, durationMs);
            }
        }
    }

    /**
     * 包装 Response 以在 doFilter 之后获取实际写回的 status。
     */
    private static class StatusCapturingResponseWrapper extends HttpServletResponseWrapper {
        private int status = HttpServletResponse.SC_OK;

        public StatusCapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
            super.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
            super.sendError(sc, msg);
        }

        public int getCapturedStatus() {
            return status;
        }
    }
}
