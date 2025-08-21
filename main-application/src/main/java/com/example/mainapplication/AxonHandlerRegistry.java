package com.example.mainapplication;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.*;

@Component
public class AxonHandlerRegistry {

  private final ApplicationContext context;

  private final Map<Class<?>, List<HandlerMethod>> eventHandlers = new HashMap<>();
  private final Map<Class<?>, List<HandlerMethod>> commandHandlers = new HashMap<>();
  private final Map<Class<?>, List<HandlerMethod>> queryHandlers = new HashMap<>();

  private final Set<Class<?>> eventHandlerClasses = new HashSet<>();
  private final Set<Class<?>> commandHandlerClasses = new HashSet<>();
  private final Set<Class<?>> queryHandlerClasses = new HashSet<>();

  public AxonHandlerRegistry(ApplicationContext context) {
    this.context = context;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void inspect() {
    String[] beanNames = context.getBeanDefinitionNames();

    for (String beanName : beanNames) {
      Object bean = context.getBean(beanName);
      Class<?> targetClass = AopUtils.getTargetClass(bean);

      // 1️⃣ Scan methods
      for (Method method : targetClass.getDeclaredMethods()) {
        scanExecutable(targetClass, method);
      }

      // 2️⃣ Scan constructors
      for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
        scanExecutable(targetClass, constructor);
      }
    }

    // Debug prints
    eventHandlers.forEach((event, methods) -> {
      System.out.println("📌 EventHandler for " + event.getName());
      methods.forEach(h -> System.out.println("   -> " + h));
    });

    commandHandlers.forEach((cmd, methods) -> {
      System.out.println("📌 CommandHandler for " + cmd.getName());
      methods.forEach(h -> System.out.println("   -> " + h));
    });

    queryHandlers.forEach((qry, methods) -> {
      System.out.println("📌 QueryHandler for " + qry.getName());
      methods.forEach(h -> System.out.println("   -> " + h));
    });

    System.out.println("📌 Event Handler Classes: " + eventHandlerClasses);
    System.out.println("📌 Command Handler Classes: " + commandHandlerClasses);
    System.out.println("📌 Query Handler Classes: " + queryHandlerClasses);
  }

  private void scanExecutable(Class<?> targetClass, Executable executable) {
    if (executable.getParameterCount() != 1) return;

    Class<?> paramType = executable.getParameterTypes()[0];

    if (executable.isAnnotationPresent(EventHandler.class)) {
      eventHandlers.computeIfAbsent(paramType, k -> new ArrayList<>())
          .add(new HandlerMethod(targetClass, executable));
      eventHandlerClasses.add(targetClass);
    } else if (executable.isAnnotationPresent(CommandHandler.class)) {
      commandHandlers.computeIfAbsent(paramType, k -> new ArrayList<>())
          .add(new HandlerMethod(targetClass, executable));
      commandHandlerClasses.add(targetClass);
    } else if (executable.isAnnotationPresent(QueryHandler.class)) {
      queryHandlers.computeIfAbsent(paramType, k -> new ArrayList<>())
          .add(new HandlerMethod(targetClass, executable));
      queryHandlerClasses.add(targetClass);
    }
  }

  // Public accessors
  public Map<Class<?>, List<HandlerMethod>> getEventHandlers() { return Collections.unmodifiableMap(eventHandlers); }
  public Map<Class<?>, List<HandlerMethod>> getCommandHandlers() { return Collections.unmodifiableMap(commandHandlers); }
  public Map<Class<?>, List<HandlerMethod>> getQueryHandlers() { return Collections.unmodifiableMap(queryHandlers); }

  public Set<Class<?>> getEventHandlerClasses() { return Collections.unmodifiableSet(eventHandlerClasses); }
  public Set<Class<?>> getCommandHandlerClasses() { return Collections.unmodifiableSet(commandHandlerClasses); }
  public Set<Class<?>> getQueryHandlerClasses() { return Collections.unmodifiableSet(queryHandlerClasses); }

  // Helper class
  public static class HandlerMethod {
    private final Class<?> beanClass;
    private final Executable executable;

    public HandlerMethod(@Nonnull Class<?> beanClass, @Nonnull Executable executable) {
      this.beanClass = beanClass;
      this.executable = executable;
    }

    public Class<?> getBeanClass() { return beanClass; }
    public Executable getExecutable() { return executable; }

    @Override
    public String toString() {
      return beanClass.getName() + "." + executable.getName() +
          "(" + executable.getParameterTypes()[0].getSimpleName() + ")";
    }
  }
}
