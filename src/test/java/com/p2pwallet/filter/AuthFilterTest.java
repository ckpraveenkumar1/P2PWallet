package com.p2pwallet.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    private final FilterChain filterChain = mock(FilterChain.class);

    private AuthFilter createFilter(String tokensConfig) {
        return new AuthFilter(tokensConfig);
    }

    @Nested
    @DisplayName("Token parsing")
    class TokenParsingTests {

        @Test
        @DisplayName("should parse valid token config")
        void shouldParseValidConfig() throws Exception {
            AuthFilter filter = createFilter("alice:tok_alice,bob:tok_bob");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/wallets");
            request.addHeader("Authorization", "Bearer tok_alice");

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus());
            assertEquals("alice", request.getAttribute("userId"));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should handle empty token config")
        void shouldHandleEmptyConfig() throws Exception {
            AuthFilter filter = createFilter("");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/wallets");
            request.addHeader("Authorization", "Bearer any_token");

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertEquals(401, response.getStatus());
        }

        @Test
        @DisplayName("should handle null token config")
        void shouldHandleNullConfig() throws Exception {
            AuthFilter filter = createFilter(null);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/wallets");
            request.addHeader("Authorization", "Bearer any_token");

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertEquals(401, response.getStatus());
        }
    }

    @Nested
    @DisplayName("Skip paths")
    class SkipPathTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "/actuator",
                "/actuator/health",
                "/actuator/prometheus",
                "/actuator/info",
                "/dashboard",
                "/metrics",
                "/swagger-ui",
                "/swagger-ui/index.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config"
        })
        @DisplayName("should skip auth for whitelisted paths")
        void shouldSkipAuth(String path) throws Exception {
            AuthFilter filter = createFilter("alice:tok_alice");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(path);
            // No Authorization header

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus());
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Authentication")
    class AuthenticationTests {

        @Test
        @DisplayName("should return 401 when Authorization header is missing")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            AuthFilter filter = createFilter("alice:tok_alice");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/wallets");

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should return 401 when Authorization header is not Bearer")
        void shouldReturn401WhenNotBearer() throws Exception {
            AuthFilter filter = createFilter("alice:tok_alice");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/wallets");
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertEquals(401, response.getStatus());
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should return 401 when token is invalid")
        void shouldReturn401WhenTokenInvalid() throws Exception {
            AuthFilter filter = createFilter("alice:tok_alice");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/wallets");
            request.addHeader("Authorization", "Bearer invalid_token");

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("Invalid bearer token"));
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should authenticate and set userId attribute for valid token")
        void shouldAuthenticateValidToken() throws Exception {
            AuthFilter filter = createFilter("alice:tok_alice,bob:tok_bob");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/transfers");
            request.addHeader("Authorization", "Bearer tok_bob");

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus());
            assertEquals("bob", request.getAttribute("userId"));
            verify(filterChain).doFilter(request, response);
        }
    }
}
