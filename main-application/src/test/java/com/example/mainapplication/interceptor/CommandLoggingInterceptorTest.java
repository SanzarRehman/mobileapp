package com.example.mainapplication.interceptor;

import com.example.mainapplication.command.CreateUserCommand;
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
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommandLoggingInterceptor.
 */
@ExtendWith(MockitoExtension.class)
class CommandLoggingInterceptorTest {

    @Mock
    private InterceptorChain interceptorChain;

    private CommandLoggingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CommandLoggingInterceptor();
        MDC.clear(); // Clear MDC before each test
    }

    @Test
    void handle_SuccessfulCommand_ShouldSetAndClearMDC() throws Exception {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<?> commandMessage = GenericCommandMessage.asCommandMessage(command);
        UnitOfWork<? extends CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(commandMessage);
        
        String expectedResult = "success";
        when(interceptorChain.proceed()).thenReturn(expectedResult);

        // When
        Object result = interceptor.handle(unitOfWork, interceptorChain);

        // Then
        assertEquals(expectedResult, result);
        verify(interceptorChain).proceed();
        
        // MDC should be cleared after processing
        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("commandId"));
    }

    @Test
    void handle_CommandThrowsException_ShouldClearMDCAndRethrowException() throws Exception {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<?> commandMessage = GenericCommandMessage.asCommandMessage(command);
        UnitOfWork<? extends CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(commandMessage);
        
        RuntimeException expectedException = new RuntimeException("Command failed");
        when(interceptorChain.proceed()).thenThrow(expectedException);

        // When & Then
        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            interceptor.handle(unitOfWork, interceptorChain);
        });

        assertEquals(expectedException, thrownException);
        verify(interceptorChain).proceed();
        
        // MDC should be cleared even after exception
        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("commandId"));
    }

    @Test
    void handle_CommandWithCorrelationId_ShouldUseExistingCorrelationId() throws Exception {
        // Given
        String existingCorrelationId = "existing-correlation-123";
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<?> commandMessage = GenericCommandMessage.asCommandMessage(command)
                .withMetaData(java.util.Collections.singletonMap("correlationId", existingCorrelationId));
        UnitOfWork<? extends CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(commandMessage);
        
        String expectedResult = "success";
        when(interceptorChain.proceed()).thenReturn(expectedResult);

        // When
        Object result = interceptor.handle(unitOfWork, interceptorChain);

        // Then
        assertEquals(expectedResult, result);
        verify(interceptorChain).proceed();
        
        // MDC should be cleared after processing
        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("commandId"));
    }
}