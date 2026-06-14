package com.djs.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("djs")
            .withUsername("djs")
            .withPassword("djs");

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>("rabbitmq:4-management")
            .withExposedPorts(5672)
            .withEnv("RABBITMQ_DEFAULT_USER", "djs")
            .withEnv("RABBITMQ_DEFAULT_PASS", "djs");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "djs");
        registry.add("spring.rabbitmq.password", () -> "djs");
    }

    @Test
    void createsReadsListsAndSoftDeletesJob() {
        ResponseEntity<Map> createResponse = restTemplate.postForEntity(
                url("/jobs"),
                fixedRateJobRequest(),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();

        String jobId = (String) createResponse.getBody().get("id");
        assertThat(jobId).isNotBlank();
        assertThat(createResponse.getBody().get("status")).isEqualTo("ACTIVE");

        ResponseEntity<Map> getResponse = restTemplate.getForEntity(url("/jobs/" + jobId), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("id", jobId);

        ResponseEntity<Map> listResponse = restTemplate.getForEntity(url("/jobs"), Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat((Iterable<?>) listResponse.getBody().get("content")).isNotEmpty();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/jobs/" + jobId),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> deletedGetResponse = restTemplate.getForEntity(url("/jobs/" + jobId), Map.class);
        assertThat(deletedGetResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deletedGetResponse.getBody()).containsKey("requestId");
    }

    @Test
    void rejectsInvalidScheduleCombination() {
        Map<String, Object> request = fixedRateJobRequest();
        request.put("cronExpression", "0 */5 * * * *");

        ResponseEntity<Map> response = restTemplate.postForEntity(url("/jobs"), request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "FIXED_RATE jobs must not define cronExpression");
    }

    private Map<String, Object> fixedRateJobRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "integration-test-job");
        request.put("description", "Created by integration test");
        request.put("type", "NOOP");
        request.put("payload", Map.of("source", "integration-test"));
        request.put("scheduleType", "FIXED_RATE");
        request.put("cronExpression", null);
        request.put("intervalSeconds", 60);
        request.put("nextRunAt", Instant.now().plusSeconds(120).toString());
        request.put("maxRetries", 3);
        request.put("retryStrategy", "EXPONENTIAL");
        request.put("retryDelaySeconds", 30);
        request.put("timeoutSeconds", 120);
        return request;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
