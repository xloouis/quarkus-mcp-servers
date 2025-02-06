package io.quarkus.mcp.servers.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;

import static io.quarkus.mcp.servers.kubernetes.MCPTestUtils.initMcpClient;
import static org.assertj.core.api.Assertions.assertThat;

public class MCPServerKubernetesIT {

  private static KubernetesMockServer mockServer;
  private static KubernetesClient kubernetesClient;
  private static McpClient client;

  @BeforeAll
  static void setUp() throws Exception {
    mockServer = new KubernetesMockServer(new Context(new ObjectMapper()),
      new MockWebServer(), new HashMap<>(), new KubernetesCrudDispatcher(), true);
    mockServer.init();
    kubernetesClient = mockServer.createClient();
    client = initMcpClient(kubernetesClient.getConfiguration().getMasterUrl());
  }

  @AfterAll
  static void closeMcpClient() {
    kubernetesClient.close();
    mockServer.destroy();
  }

  @ParameterizedTest(name = "{index}: mcp-server-kubernetes provides the {0} tool")
  @ValueSource(strings = {
    "configuration_get",
    "namespaces_list",
    "pods_list",
    "pods_list_in_namespace",
    "pods_get",
    "pods_log",
    "pods_delete",
    "pods_run"
  })
  public void tools(String toolName) {
    assertThat(client.listTools())
      .extracting(ToolSpecification::name)
      .contains(toolName);
  }

  @Test
  void namespaces_list() {
    kubernetesClient.namespaces()
      .resource(new NamespaceBuilder().withNewMetadata().withName("a-namespace-to-list").endMetadata().build())
      .create();
    assertThat(client.executeTool(ToolExecutionRequest.builder().name("namespaces_list").arguments("{}").build()))
      .isNotBlank()
      .satisfies(nsList -> assertThat(unmarshalList(nsList, Namespace.class))
        .extracting("metadata.name")
        .contains("a-namespace-to-list"));
  }

  @Nested
  class PodOperations {

    @Test
    void pods_list() {
      kubernetesClient.pods()
        .resource(new PodBuilder().withNewMetadata().withName("a-pod-to-list").endMetadata().build())
        .create();
      assertThat(client.executeTool(ToolExecutionRequest.builder().name("pods_list").arguments("{}").build()))
        .isNotBlank()
        .satisfies(pList -> assertThat(unmarshalList(pList, Pod.class))
          .extracting("metadata.name")
          .contains("a-pod-to-list"));
    }

    @Test
    void pods_get() {
      kubernetesClient.pods()
        .resource(new PodBuilder()
          .withNewMetadata().withName("a-pod-to-get").endMetadata()
          .withNewSpec().withServiceAccount("default").endSpec()
          .build())
        .create();
      final var response = client.executeTool(ToolExecutionRequest.builder().name("pods_get").arguments("{\"name\":\"a-pod-to-get\"}").build());
      assertThat(response)
        .isNotBlank()
        .satisfies(p -> assertThat(unmarshal(p, Pod.class))
          .extracting("metadata.name", "spec.serviceAccount")
          .contains("a-pod-to-get", "default"));
    }
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> unmarshalList(String json, Class<T> clazz) {
    return kubernetesClient.getKubernetesSerialization().unmarshal(json, List.class);
  }

  private <T> T unmarshal(String json, Class<T> clazz) {
    return kubernetesClient.getKubernetesSerialization().unmarshal(json, clazz);
  }

}
