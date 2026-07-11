package com.cutegoals.web.config;

import com.cutegoals.common.constant.AuthConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.io.IOException;
import java.util.List;

/**
 * Authentication interceptor that runs after the DispatcherServlet has resolved
 * the target handler. This lets Spring return 404/405 for unknown or unsupported
 * endpoints while still enforcing authentication for real endpoints.
 */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Allow pre-flight CORS requests
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Skip public endpoints (CSRF and JWT filters already handle them)
        for (String p : WebSecurityConfig.PUBLIC_PATHS) {
            if (path.equals(p) || path.startsWith(p + "/")) {
                return true;
            }
        }

        // If no handler was resolved, the DispatcherServlet will return 404 on its own.
        // If the handler is the static-resource handler, it will return 404 for missing resources.
        if (handler == null || handler instanceof ResourceHttpRequestHandler) {
            return true;
        }

        // The JWT filter sets the account id when a valid token is present.
        Object accountId = request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID);
        if (accountId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"data\":null}");
            return false;
        }

        return true;
    }
}
