package com.enterprise.flashsale.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String CACHE_PREFIX = "idempotency:";
    private final StringRedisTemplate redisTemplate;

    public IdempotencyInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) throws Exception {

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);

        if (idempotent == null) {
            return true;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER.toLowerCase());
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Missing required header: {}", IDEMPOTENCY_HEADER);
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"BAD_REQUEST\",\"message\":\"Missing required header: " + IDEMPOTENCY_HEADER + "\"}");
            return false;
        }

        String redisKey = CACHE_PREFIX + idempotencyKey;
        String cachedResponse = redisTemplate.opsForValue().get(redisKey);

        if (cachedResponse != null) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/json");
            response.getWriter().write(cachedResponse);
            return false;
        }

        return true;
    }
}
