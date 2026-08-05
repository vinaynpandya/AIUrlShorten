package com.assignment.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InstanceHeaderFilterTest {

    @Test
    void addsInstanceHeaderAndInvokesFilterChain() throws Exception {
        InstanceHeaderFilter filter = new InstanceHeaderFilter("test-instance");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-App-Instance", "test-instance");
        verify(filterChain).doFilter(request, response);
    }
}
