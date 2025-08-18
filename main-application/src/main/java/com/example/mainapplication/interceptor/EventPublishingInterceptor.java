package com.example.mainapplication.interceptor;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.springframework.stereotype.Component;

/**
 * Interceptor that previously published events to the custom axon server after they are applied.
 * Event forwarding is now handled via an EventBus dispatch interceptor. This handler interceptor
 * is kept as a no-op to avoid duplicate publishing and potential recursion.
 */
@Component
public class EventPublishingInterceptor implements MessageHandlerInterceptor<EventMessage<?>> {

    @Override
    public Object handle(UnitOfWork<? extends EventMessage<?>> unitOfWork, InterceptorChain interceptorChain) throws Exception {
        // No-op to avoid double-publishing. Actual forwarding is via EventBus dispatch interceptor.
        return interceptorChain.proceed();
    }
}