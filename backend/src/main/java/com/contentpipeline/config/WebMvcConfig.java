package com.contentpipeline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    //either the allowed origin in data base or local host if default
    @Value("${cors.allowed-origins:http://localhost:3000}") 
    private String allowedOrigins;


    //Cors is cross-origin resource sharing
    //basicly for figuring out who can call back end
    //The 2 methods pretty much just add rules, addCors uses add mapping
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(allowedOrigins.split(","))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Content-Disposition")
            .allowCredentials(true)
            .maxAge(3600);

        // SSE endpoint needs longer preflight cache
        registry.addMapping("/api/*/pipeline-runs/*/events")
            .allowedOriginPatterns(allowedOrigins.split(","))
            .allowedMethods("GET")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(86400);
    }
}
