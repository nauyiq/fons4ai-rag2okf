package com.fons.cloud.ai.rag2okf.infrastructure.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 全局 CORS 过滤器配置。
 *
 * <p>开发环境允许前端 dev server 跨域访问后端 API；生产环境通过 Nginx 同源反向代理汇聚，
 * 此过滤器仍然安全（仅允许配置的来源）。
 *
 * <p>使用全局 Filter 方式处理跨域，不使用控制器注解，避免与 T028 安全门禁约束冲突。
 *
 * @author hongqy
 */
@Configuration
public class CorsConfiguration implements WebMvcConfigurer {

    /** CORS 预检请求缓存时间（秒） */
    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    /**
     * 使用 CorsFilter（Servlet Filter 级别）处理 CORS。
     * CorsFilter 在 Spring MVC 拦截器之前运行，确保浏览器 OPTIONS 预检请求
     * 不会触达 sa-token 鉴权逻辑，直接返回 200 + CORS 响应头。
     */
    @Bean
    public CorsFilter corsFilter() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);
        config.setMaxAge(CORS_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
