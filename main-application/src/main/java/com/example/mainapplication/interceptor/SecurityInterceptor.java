package com.example.mainapplication.interceptor;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Security interceptor for Axon commands to ensure authentication context is available
 */
@Component
public class SecurityInterceptor implements MessageHandlerInterceptor<CommandMessage<?>> {

    @Override
    public Object handle(UnitOfWork<? extends CommandMessage<?>> unitOfWork, InterceptorChain interceptorChain) throws Exception {
        CommandMessage<?> command = unitOfWork.getMessage();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//testttt

//        if (authentication == null || !authentication.isAuthenticated()) {
//            throw new SecurityException("Command processing requires authentication: " + command.getCommandName());
//        }

        // Add user context to command metadata
//        final CommandMessage<?> enhancedCommand = command.andMetaData(
//            command.getMetaData()
//                .and("userId", authentication.getName())
//                .and("authorities", authentication.getAuthorities().toString())
//        );

        final CommandMessage<?> enhancedCommand = command.andMetaData(
            command.getMetaData()
                .and("userId", "sanzar")
                .and("authorities", "ROLE")
        );

        // Update the unit of work with the enhanced command
        unitOfWork = unitOfWork.transformMessage(commandMessage -> enhancedCommand);

        return interceptorChain.proceed();
    }
}