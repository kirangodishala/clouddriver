/*
 * Copyright 2024 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
public class PostgresMigrationContainerTest extends BaseContainerTest {

  private static final Logger logger =
      LoggerFactory.getLogger(PostgresMigrationContainerTest.class);

  private static final String POSTGRES_NETWORK_ALIAS = "postgresHost";

  private static final int POSTGRES_PORT = 5432;

  private PostgreSQLContainer<?> postgres;

  private GenericContainer<?> clouddriverInitialContainer;

  private static DockerImageName previousDockerImageName;

  private String jdbcUrl = "";

  @BeforeAll
  static void setupInitial() {
    String fullDockerImageName = System.getenv("PREVIOUS_FULL_DOCKER_IMAGE_NAME");
    // Skip the tests if there's no docker image.  This allows gradlew build to work.
    assumeTrue(fullDockerImageName != null);
    previousDockerImageName = DockerImageName.parse(fullDockerImageName);
  }

  @BeforeEach
  void setup() throws Exception {
    postgres =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("clouddriver")
            .withUsername("postgres")
            .withPassword("postgres")
            .withNetwork(network)
            .withNetworkAliases(POSTGRES_NETWORK_ALIAS)
            .withInitScript("postgres_init.sql")
            .withReuse(true);
    postgres.start();
    jdbcUrl =
        String.format("jdbc:postgresql://%s:%d/clouddriver", POSTGRES_NETWORK_ALIAS, POSTGRES_PORT);

    // Start the first clouddriver(from previous release) container so that all the db changelog
    // sets are executed
    clouddriverInitialContainer =
        new GenericContainer(previousDockerImageName)
            .withNetwork(network)
            .withExposedPorts(7002)
            .waitingFor(Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)))
            .dependsOn(postgres)
            .withEnv("SPRING_APPLICATION_JSON", getSpringApplicationJson());
    clouddriverInitialContainer.start();
    Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger);
    clouddriverInitialContainer.followOutput(logConsumer);
    clouddriverInitialContainer.stop();

    // Start the second clouddriver(latest) container to validate migration
    clouddriverContainer
        .dependsOn(postgres)
        .withEnv("SPRING_APPLICATION_JSON", getSpringApplicationJson())
        .start();

    clouddriverContainer.followOutput(logConsumer);
  }

  private String getSpringApplicationJson() throws JsonProcessingException {
    logger.info("----------- jdbcUrl: '{}'", jdbcUrl);
    Map<String, String> connectionPool =
        Map.of("jdbcUrl", jdbcUrl, "user", "clouddriver_service", "password", "c10uddriver");
    Map<String, String> migration =
        Map.of("jdbcUrl", jdbcUrl, "user", "clouddriver_migrate", "password", "c10uddriver");

    Map<String, Object> properties =
        Map.of(
            "sql.enabled",
            "true",
            "services.fiat.baseUrl",
            "http://nowhere",
            "sql.connectionPool",
            connectionPool,
            "redis.enabled",
            "false",
            "sql.migration",
            migration);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(properties);
  }

  @AfterAll
  void cleanupOnce() {
    if (clouddriverContainer != null) {
      clouddriverContainer.stop();
    }

    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void testHealthCheckWithPostgres() throws Exception {
    super.testHealthCheck();
  }
}
