package com.p2pwallet.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final FilterChain filterChain = mock(FilterChain.class);

    @Test
    @DisplayName("should generate correlation ID when not provided in request")
    void shouldGenerateCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/wallets");
        request.setMethod("GET");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        String correlationId = response.getHeader("X-Correlation-Id");
        assertNotNull(correlationId);
        assertFalse(correlationId.isBlank());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should use correlation ID from request header when provided")
    void shouldUseProvidedCorrelationId() throws Exception {
        String providedId = "my-custom-correlation-id";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/transfers");
        request.setMethod("POST");
        request.addHeader("X-Correlation-Id", providedId);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(providedId, response.getHeader("X-Correlation-Id"));
    }

    @Test
    @DisplayName("should generate new ID when header is blank")
    void shouldGenerateWhenHeaderBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/wallets");
        request.setMethod("GET");
        request.addHeader("X-Correlation-Id", "   ");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        String correlationId = response.getHeader("X-Correlation-Id");
        assertNotNull(correlationId);
        assertNotEquals("   ", correlationId);
    }

    @Test
    @DisplayName("should clear MDC after filter chain completes")
    void shouldClearMdcAfterCompletion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/wallets");
        request.setMethod("GET");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        // MDC should be cleared after the filter chain
        assertNull(MDC.get("correlation_id"));
        assertNull(MDC.get("method"));
        assertNull(MDC.get("path"));
    }

    @Test
    @DisplayName("should clear MDC even when filter chain throws exception")
    void shouldClearMdcOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/wallets");
        request.setMethod("GET");

        MockHttpServletResponse response = new MockHttpServletResponse();

        doThrow(new RuntimeException("test error")).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, filterChain));

        // MDC should still be cleared
        assertNull(MDC.get("correlation_id"));
        assertNull(MDC.get("method"));
        assertNull(MDC.get("path"));
    }
}
