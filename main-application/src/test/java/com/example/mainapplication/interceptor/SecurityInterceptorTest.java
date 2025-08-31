package com.example.mainapplication.interceptor;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests for security interceptor
 */
@ExtendWith(MockitoExtension.class)
class SecurityInterceptorTest {

    @Mock
    private InterceptorChain interceptorChain;

    private SecurityInterceptor securityInterceptor;

    @BeforeEach
    void setUp() {
        securityInterceptor = new SecurityInterceptor();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowCommandWithAuthentication() throws Exception {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "testuser", 
                "password", 
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        CommandMessage<String> command = GenericCommandMessage.asCommandMessage("test-command");
        UnitOfWork<CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(command);

        when(interceptorChain.proceed()).thenReturn("success");

        // When
        Object result = securityInterceptor.handle(unitOfWork, interceptorChain);

        // Then
        assertEquals("success", result);
        // Note: The command metadata is enhanced in the interceptor but we can't easily verify it in this test
        // since the unit of work transformation happens internally
    }

    @Test
    void shouldRejectCommandWithoutAuthentication() {
        // Given
        SecurityContextHolder.clearContext(); // No authentication

        CommandMessage<String> command = GenericCommandMessage.asCommandMessage("test-command");
        UnitOfWork<CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(command);

        // When & Then
        assertThrows(SecurityException.class, () -> 
            securityInterceptor.handle(unitOfWork, interceptorChain)
        );
    }

    @Test
    void shouldRejectCommandWithUnauthenticatedUser() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", "password");
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        CommandMessage<String> command = GenericCommandMessage.asCommandMessage("test-command");
        UnitOfWork<CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(command);

        // When & Then
        assertThrows(SecurityException.class, () -> 
            securityInterceptor.handle(unitOfWork, interceptorChain)
        );
    }
}