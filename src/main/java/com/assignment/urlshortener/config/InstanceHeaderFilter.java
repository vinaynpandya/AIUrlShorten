package com.assignment.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InstanceHeaderFilter extends OncePerRequestFilter {

    private static final String INSTANCE_HEADER = "X-App-Instance";

    private final String instanceName;

    public InstanceHeaderFilter(@Value("${app.instance-name:local-instance}") String instanceName) {
        this.instanceName = instanceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(INSTANCE_HEADER, instanceName);
        filterChain.doFilter(request, response);
    }
}
