package com.contentanalytics.interceptor;

import com.contentanalytics.config.RateLimitingConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final RateLimitingConfig rateLimitingConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        String requestPath = request.getRequestURI();

        // Apply rate limiting to login endpoint
        if (requestPath.contains("/auth/login")) {
            return checkRateLimit(request, response, rateLimitingConfig.getLoginBucket(clientIp), clientIp, "login");
        }

        // Apply rate limiting to register endpoint
        if (requestPath.contains("/auth/register")) {
            return checkRateLimit(request, response, rateLimitingConfig.getRegisterBucket(clientIp), clientIp, "register");
        }

        return true;
    }

    /**
     * Check if request is within rate limit
     */
    private boolean checkRateLimit(HttpServletRequest request, HttpServletResponse response, Bucket bucket,
                                   String clientIp, String endpoint) throws Exception {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Request is allowed
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        } else {
            // Rate limit exceeded
            long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            response.setStatus(429); // SC_TOO_MANY_REQUESTS
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Too many " + endpoint + " attempts. Try again in " + waitForRefill + " seconds\"}"
            );

            log.warn("Rate limit exceeded for {} from IP: {}", endpoint, clientIp);
            return false;
        }
    }

    /**
     * Get client IP address from request (handles proxies)
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}