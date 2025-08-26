package com.example.mainapplication.util;

import com.example.grpc.common.SubmitCommandRequest;
import com.example.grpc.common.SubmitEventRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventDeserializer {

  private final ObjectMapper objectMapper;

  public EventDeserializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Reconstruct payload from SubmitCommandRequest
   *
   * @param request gRPC command request
   * @param useProtobufStruct true if payload is a Protobuf Struct
   * @return Map representation of payload
   */
  public Map<String, Object> reconstructPayload(SubmitEventRequest request, boolean useProtobufStruct) throws InvalidProtocolBufferException {
    if (useProtobufStruct) {
      // Deserialize Protobuf Struct -> Map
      Struct struct = Struct.parseFrom(request.getPayload());
      return structToMap(struct);
    } else {
      // Deserialize JSON bytes -> Map
      String json = request.getPayload().toStringUtf8();
      try {
        return objectMapper.readValue(json, Map.class);
      } catch (Exception e) {
        throw new RuntimeException("Failed to deserialize JSON payload", e);
      }
    }
  }

  private Map<String, Object> structToMap(Struct struct) {
    Map<String, Object> map = new HashMap<>();
    for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
      map.put(entry.getKey(), valueToObject(entry.getValue()));
    }
    return map;
  }

  private Object valueToObject(Value value) {
    switch (value.getKindCase()) {
      case NULL_VALUE: return null;
      case STRING_VALUE: return value.getStringValue();
      case NUMBER_VALUE: return value.getNumberValue();
      case BOOL_VALUE: return value.getBoolValue();
      case STRUCT_VALUE: return structToMap(value.getStructValue());
      case LIST_VALUE:
        List<Value> list = value.getListValue().getValuesList();
        return list.stream().map(this::valueToObject).toList();
      default: return null;
    }
  }
}
