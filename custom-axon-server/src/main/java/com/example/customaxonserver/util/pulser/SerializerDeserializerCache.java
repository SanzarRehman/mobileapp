package com.example.customaxonserver.util.pulser;

import com.example.grpc.common.SubmitEventRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;

public class SerializerDeserializerCache {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final ConcurrentHashMap<String, Class<?>> classCache = new ConcurrentHashMap<>();

  // Deserialize to object of type className
  public static <T> T deserialize(String className, byte[] data) throws Exception {
    Class<?> clazz = classCache.computeIfAbsent(className, name -> {
      try {
        return Class.forName(name);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    });

    @SuppressWarnings("unchecked")
    T obj = (T) mapper.readValue(data, clazz);
    return obj;
  }

  // Serialize any object to byte[]
  public static byte[] serialize(SubmitEventRequest obj) throws Exception {
    return obj.toByteArray();
  }
}
