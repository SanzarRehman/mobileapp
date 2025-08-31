//package com.example.customaxonserver.health;
//
//import org.apache.kafka.clients.admin.AdminClient;
//import org.apache.kafka.clients.admin.DescribeClusterResult;
//import org.springframework.boot.actuate.health.Health;
//import org.springframework.boot.actuate.health.HealthIndicator;
//import org.springframework.kafka.core.KafkaAdmin;
//import org.springframework.stereotype.Component;
//
//import java.util.concurrent.TimeUnit;
//
//@Component
//public class KafkaHealthIndicator implements HealthIndicator {
//
//    private final KafkaAdmin kafkaAdmin;
//
//    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
//        this.kafkaAdmin = kafkaAdmin;
//    }
//
//    @Override
//    public Health health() {
//        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
//
//            DescribeClusterResult clusterResult = adminClient.describeCluster();
//
//            // Wait for cluster info with timeout
//            String clusterId = clusterResult.clusterId().get(5, TimeUnit.SECONDS);
//            int nodeCount = clusterResult.nodes().get(5, TimeUnit.SECONDS).size();
//
//            return Health.up()
//                    .withDetail("kafka", "Available")
//                    .withDetail("clusterId", clusterId)
//                    .withDetail("nodeCount", nodeCount)
//                    .build();
//
//        } catch (Exception e) {
//            return Health.down()
//                    .withDetail("kafka", "Unavailable")
//                    .withDetail("error", e.getMessage())
//                    .withException(e)
//                    .build();
//        }
//    }
//}