package com.example.mainapplication.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private CorrelationIdFilter correlationIdFilter;

    @BeforeEach
    void setUp() {
        correlationIdFilter = new CorrelationIdFilter();
        MDC.clear();
    }

    @Test
    void doFilterInternal_WhenCorrelationIdExists_ShouldUseExistingId() throws ServletException, IOException {
        // Given
        String existingCorrelationId = "existing-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(existingCorrelationId);

        // When
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setHeader("X-Correlation-ID", existingCorrelationId);
        verify(filterChain).doFilter(request, response);
        
        // MDC should be cleared after processing
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void doFilterInternal_WhenCorrelationIdMissing_ShouldGenerateNewId() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        // When
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setHeader(eq("X-Correlation-ID"), any(String.class));
        verify(filterChain).doFilter(request, response);
        
        // MDC should be cleared after processing
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void doFilterInternal_WhenCorrelationIdEmpty_ShouldGenerateNewId() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("");

        // When
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setHeader(eq("X-Correlation-ID"), any(String.class));
        verify(filterChain).doFilter(request, response);
        
        // MDC should be cleared after processing
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void doFilterInternal_WhenExceptionOccurs_ShouldClearMDC() throws ServletException, IOException {
        // Given
        String correlationId = "test-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(correlationId);
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);

        // When & Then
        try {
            correlationIdFilter.doFilterInternal(request, response, filterChain);
        } catch (ServletException e) {
            // Expected exception
        }

        // MDC should be cleared even when exception occurs
        assertThat(MDC.get("correlationId")).isNull();
    }
}