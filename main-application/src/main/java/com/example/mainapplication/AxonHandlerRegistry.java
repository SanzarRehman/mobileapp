package com.example.mainapplication;

import jakarta.annotation.Nonnull;
import org.apache.pulsar.shade.org.apache.avro.Schema;
import org.apache.pulsar.shade.org.apache.avro.reflect.ReflectData;
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
  private final Map<String, Schema> eventClassToAvroSchema = new HashMap<>();

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

      for (Method method : targetClass.getDeclaredMethods()) {
        scanExecutable(targetClass, method);
      }

      for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
        scanExecutable(targetClass, constructor);
      }
    }

    // Debug prints
    eventHandlers.forEach((event, methods) -> {

      methods.forEach(h -> System.out.println("   -> " + h));
    });

    commandHandlers.forEach((cmd, methods) -> {

      methods.forEach(h -> System.out.println("   -> " + h));
    });

    queryHandlers.forEach((qry, methods) -> {

      methods.forEach(h -> System.out.println("   -> " + h));
    });

  }

  private void scanExecutable(Class<?> targetClass, Executable executable) {
    if (executable.getParameterCount() != 1) return;

    Class<?> paramType = executable.getParameterTypes()[0];

    if (executable.isAnnotationPresent(EventHandler.class)) {
      eventHandlers.computeIfAbsent(paramType, k -> new ArrayList<>())
          .add(new HandlerMethod(targetClass, executable));
      eventHandlerClasses.add(targetClass);

      // Generate Avro schema and use fully qualified class name as key
      Schema avroSchema = ReflectData.get().getSchema(paramType);
      eventClassToAvroSchema.put(paramType.getName(), avroSchema);
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
  public Map<String, Schema> getEventClassToAvroSchema() { return Collections.unmodifiableMap(eventClassToAvroSchema); }


  public Set<Class<?>> getEventHandlerClasses() { return Collections.unmodifiableSet(eventHandlerClasses); }
  public Set<Class<?>> getCommandHandlerClasses() { return Collections.unmodifiableSet(commandHandlerClasses); }
  public Set<Class<?>> getQueryHandlerClasses() { return Collections.unmodifiableSet(queryHandlerClasses); }

  // Enhanced accessors for type names (needed for gRPC registration)
  public Set<String> getCommandTypes() {
    return commandHandlers.keySet().stream()
        .map(Class::getName)
        .collect(java.util.stream.Collectors.toSet());
  }

  public Set<String> getQueryTypes() {
    return queryHandlers.keySet().stream()
        .map(Class::getName)
        .collect(java.util.stream.Collectors.toSet());
  }

  public Set<String> getEventTypes() {
    return eventHandlers.keySet().stream()
        .map(Class::getName)
        .collect(java.util.stream.Collectors.toSet());
  }

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
