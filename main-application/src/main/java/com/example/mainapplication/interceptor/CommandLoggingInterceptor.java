//package com.example.mainapplication.interceptor;
//
//import org.axonframework.commandhandling.CommandMessage;
//import org.axonframework.messaging.InterceptorChain;
//import org.axonframework.messaging.MessageHandlerInterceptor;
//import org.axonframework.messaging.unitofwork.UnitOfWork;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.slf4j.MDC;
//import org.springframework.stereotype.Component;
//
//import java.util.UUID;
//
///**
// * Command interceptor that adds logging and correlation tracking for commands.
// */
//@Component
//public class CommandLoggingInterceptor implements MessageHandlerInterceptor<CommandMessage<?>> {
//
//    private static final Logger logger = LoggerFactory.getLogger(CommandLoggingInterceptor.class);
//    private static final String CORRELATION_ID_KEY = "correlationId";
//    private static final String COMMAND_ID_KEY = "commandId";
//
//    @Override
//    public Object handle(UnitOfWork<? extends CommandMessage<?>> unitOfWork, InterceptorChain interceptorChain) throws Exception {
//        CommandMessage<?> command = unitOfWork.getMessage();
//        String correlationId = getOrCreateCorrelationId(command);
//        String commandId = command.getIdentifier();
//
//        // Set MDC for logging context
//        MDC.put(CORRELATION_ID_KEY, correlationId);
//        MDC.put(COMMAND_ID_KEY, commandId);
//
//        try {
//            logger.info("Processing command: {} with ID: {} and correlation ID: {}",
//                    command.getCommandName(), commandId, correlationId);
//
//            long startTime = System.currentTimeMillis();
//            Object result = interceptorChain.proceed();
//            long duration = System.currentTimeMillis() - startTime;
//
//            logger.info("Successfully processed command: {} in {}ms",
//                    command.getCommandName(), duration);
//
//            return result;
//        } catch (Exception e) {
//            logger.error("Error processing command: {} with ID: {}",
//                    command.getCommandName(), commandId, e);
//            throw e;
//        } finally {
//            // Clean up MDC
//            MDC.remove(CORRELATION_ID_KEY);
//            MDC.remove(COMMAND_ID_KEY);
//        }
//    }
//
//    private String getOrCreateCorrelationId(CommandMessage<?> command) {
//        return command.getMetaData()
//                .getOrDefault(CORRELATION_ID_KEY, UUID.randomUUID().toString())
//                .toString();
//    }
//}