package com.example.axon.service;

import com.example.grpc.common.CommandHandlingServiceGrpc;
import com.example.grpc.common.SubmitCommandRequest;
import com.example.grpc.common.SubmitCommandResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HighPerformanceCommandRouter {

  private final ObjectMapper objectMapper;  // Reuse across calls
  private final Map<Class<?>, Field> aggregateIdFieldCache = new ConcurrentHashMap<>();
  private final String masterGrpcServerUrl;
  private final Integer masterGrpcServerPort;
  public HighPerformanceCommandRouter(ObjectMapper objectMapper, @org.springframework.beans.factory.annotation.Value("${app.grpc-server.url:localhost}")String masterGrpcServerUrl,@org.springframework.beans.factory.annotation.Value("${app.grpc-server.port:9060}")int masterGrpcServerPort) {
    this.objectMapper = objectMapper;
    this.masterGrpcServerUrl = masterGrpcServerUrl;
    this.masterGrpcServerPort = masterGrpcServerPort;
  }

  /**
   * Route a dynamic Axon CommandMessage via gRPC.
   *
   * @param commandMessage    The Axon command
   * @param responseType      Expected response type (e.g., String.class)   gRPC channel to the remote server
   * @param useProtobufStruct If true, serialize payload as Protobuf Struct, else JSON bytes
   * @param <T>               Response type
   * @return Deserialized response
   */
  public <T> T routeCommand(CommandMessage<?> commandMessage,
                            Class<T> responseType,
                            boolean useProtobufStruct) {

    Object payloadObj = commandMessage.getPayload();
    ByteString payloadBytes;

    try {
      if (useProtobufStruct) {
        Map<String, Object> map = objectMapper.convertValue(payloadObj, Map.class);
        Struct.Builder structBuilder = Struct.newBuilder();
        map.forEach((k, v) -> structBuilder.putFields(k, convertToValue(v)));
        payloadBytes = structBuilder.build().toByteString();
      } else {
        payloadBytes = ByteString.copyFromUtf8(objectMapper.writeValueAsString(payloadObj));
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize command payload", e);
    }

    ManagedChannel channel = ManagedChannelBuilder
        .forAddress(masterGrpcServerUrl, masterGrpcServerPort)
        .usePlaintext()
        .build();

    String aggregateId = extractAggregateId(payloadObj);

    SubmitCommandRequest request = SubmitCommandRequest.newBuilder()
        .setCommandId(commandMessage.getIdentifier())
        .setAggregateId(aggregateId)
        .setCommandType(commandMessage.getPayloadType().getName())
        .setPayload(payloadBytes)
        .build();

    CommandHandlingServiceGrpc.CommandHandlingServiceBlockingStub stub =
        CommandHandlingServiceGrpc.newBlockingStub(channel);

    SubmitCommandResponse response = stub.submitCommand(request);

    if (responseType == String.class) {
      return responseType.cast(response.getResult());
    }

    return null; // Extend as needed for other response types
  }

  /**
   * Convert Java object to Protobuf Value.
   * Can be extended recursively for nested maps or lists.
   */
  private Value convertToValue(Object obj) {
    if (obj == null) return Value.newBuilder().setNullValueValue(0).build();
    if (obj instanceof String) return Value.newBuilder().setStringValue((String) obj).build();
    if (obj instanceof Number) return Value.newBuilder().setNumberValue(((Number) obj).doubleValue()).build();
    if (obj instanceof Boolean) return Value.newBuilder().setBoolValue((Boolean) obj).build();
    // Fallback: convert to string
    return Value.newBuilder().setStringValue(obj.toString()).build();
  }

  /**
   * Extract the aggregate ID from a payload using @TargetAggregateIdentifier.
   * Cached for high-performance.
   */
  private String extractAggregateId(Object payload) {
    Field field = aggregateIdFieldCache.computeIfAbsent(payload.getClass(), clazz -> {
      for (Field f : clazz.getDeclaredFields()) {
        if (f.isAnnotationPresent(TargetAggregateIdentifier.class)) {
          f.setAccessible(true);
          return f;
        }
      }
      throw new RuntimeException("No @TargetAggregateIdentifier field in " + clazz.getName());
    });
    try {
      Object value = field.get(payload);
      return value != null ? value.toString() : java.util.UUID.randomUUID().toString();
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to read aggregate ID", e);
    }
  }
}
