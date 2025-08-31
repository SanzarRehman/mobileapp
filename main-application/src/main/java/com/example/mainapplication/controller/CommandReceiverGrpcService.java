package com.example.mainapplication.controller;

import com.example.axon.util.CommandDeserializer;
import com.example.grpc.common.CommandHandlingServiceGrpc;
import com.example.grpc.common.SubmitCommandRequest;
import com.example.grpc.common.SubmitCommandResponse;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

@GrpcService
public class CommandReceiverGrpcService extends CommandHandlingServiceGrpc.CommandHandlingServiceImplBase {

  private static final Logger logger = LoggerFactory.getLogger(CommandReceiverGrpcService.class);

  private final CommandGateway commandGateway;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final CommandDeserializer commandDeserializer = new CommandDeserializer(objectMapper);
  public CommandReceiverGrpcService(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  @Override
  public void submitCommand(SubmitCommandRequest request, StreamObserver<SubmitCommandResponse> responseObserver) {
    logger.info("📥 gRPC: Received command: {} for aggregate: {}",
        request.getCommandType(), request.getAggregateId());

    try {

      String className = request.getCommandType();
      Map<String, Object> payloadMap = commandDeserializer.reconstructPayload(request, false); // or true for Struct

      // Dynamically build command object
      Class<?> commandClass = Class.forName(className);
      Object command = objectMapper.convertValue(payloadMap, commandClass);
      // Rebuild the actual command object


      //  Execute with Axon CommandGateway
      Object result = commandGateway.sendAndWait(command);

      //  Send back response
      SubmitCommandResponse response = SubmitCommandResponse.newBuilder()
          .setSuccess(true)
          .setMessage("Command processed successfully")
          .setResult(result != null ? result.toString() : "null")
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();

      logger.info(" gRPC: Successfully executed {} with result: {}", request.getCommandType(), result);

    } catch (Exception e) {
      logger.error(" Error processing command {}: {}", request.getCommandId(), e.getMessage(), e);

      SubmitCommandResponse errorResponse = SubmitCommandResponse.newBuilder()
          .setSuccess(false)
          .setMessage("Failed to process command: " + e.getMessage())
          .setErrorCode("PROCESSING_ERROR")
          .build();

      responseObserver.onNext(errorResponse);
      responseObserver.onCompleted();
    }
  }

}

