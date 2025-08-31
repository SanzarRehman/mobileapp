package com.example.mainapplication.interceptor;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;

/**
 * Legacy no-op interceptor. Deactivated (no @Component) to avoid registration.
 */
public class EventPublishingInterceptor implements MessageHandlerInterceptor<EventMessage<?>> {

    @Override
    public Object handle(UnitOfWork<? extends EventMessage<?>> unitOfWork, InterceptorChain interceptorChain) throws Exception {
        // No-op intentionally
        return interceptorChain.proceed();
    }
}