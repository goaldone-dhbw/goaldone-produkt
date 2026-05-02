package de.goaldone.authservice.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Web configuration for Auth Service.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<CacheControlFilter> cacheControlFilter() {
        FilterRegistrationBean<CacheControlFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CacheControlFilter());
        registrationBean.addUrlPatterns("/oauth2/jwks");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    private static class CacheControlFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            chain.doFilter(request, response);

            if (httpRequest.getRequestURI().equals("/oauth2/jwks")) {
                httpResponse.setHeader("Cache-Control", "public, max-age=3600");
                // Remove default Spring Security headers if they exist
                httpResponse.setHeader("Pragma", null);
                httpResponse.setHeader("Expires", null);
            }
        }
    }
}
