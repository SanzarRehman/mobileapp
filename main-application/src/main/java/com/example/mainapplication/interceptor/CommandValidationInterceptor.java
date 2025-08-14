package com.example.mainapplication.interceptor;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Command interceptor that validates command payloads using Bean Validation.
 */
@Component
public class CommandValidationInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(CommandValidationInterceptor.class);
    private final Validator validator;

    public CommandValidationInterceptor(Validator validator) {
        this.validator = validator;
    }

    @Override
    public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
            List<? extends CommandMessage<?>> messages) {
        
        return (index, command) -> {
            logger.debug("Validating command: {}", command.getCommandName());
            
            Set<ConstraintViolation<Object>> violations = validator.validate(command.getPayload());
            
            if (!violations.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Command validation failed: ");
                for (ConstraintViolation<Object> violation : violations) {
                    errorMessage.append(violation.getPropertyPath())
                            .append(" ")
                            .append(violation.getMessage())
                            .append("; ");
                }
                
                logger.error("Command validation failed for {}: {}", 
                        command.getCommandName(), errorMessage.toString());
                throw new IllegalArgumentException(errorMessage.toString());
            }
            
            logger.debug("Command validation passed for: {}", command.getCommandName());
            return command;
        };
    }
}