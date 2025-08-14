package com.example.mainapplication.service;

import com.example.mainapplication.command.CreateUserCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomServerCommandService.
 */
@ExtendWith(MockitoExtension.class)
class CustomServerCommandServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private CustomServerCommandService service;
    private final String customServerUrl = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        service = new CustomServerCommandService(restTemplate, objectMapper, customServerUrl);
    }

    @Test
    void routeCommand_SuccessfulResponse_ShouldReturnResult() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<CreateUserCommand> commandMessage = GenericCommandMessage.asCommandMessage(command);
        
        String expectedResponse = "Command processed successfully";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);
        
        when(restTemplate.exchange(
                eq(customServerUrl + "/api/commands"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        // When
        String result = service.