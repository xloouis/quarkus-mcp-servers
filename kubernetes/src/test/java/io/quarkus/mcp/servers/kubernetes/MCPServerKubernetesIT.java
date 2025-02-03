package io.quarkus.mcp.servers.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class MCPServerKubernetesIT {

  private static KubernetesMockServer mockServer;
  private static KubernetesClient kubernetesClient;
  private static McpClient client;

  @BeforeAll
  static void initMcpClient() throws Exception {
    mockServer = new KubernetesMockServer(new Context(new ObjectMapper()),
      new MockWebServer(), new HashMap<>(), new KubernetesCrudDispatcher(), true);
    mockServer.init();
    kubernetesClient = mockServer.createClient();
    final var kubeConfigArgs = List.of(
      "-Dquarkus.kubernetes-client.api-server-url=" + kubernetesClient.getConfiguration().getMasterUrl(),
      "-Dquarkus.kubernetes-client.trust-certs=true",
      "-Dquarkus.kubernetes-client.namespace=test"
    );
    final List<String> command = new ArrayList<>();
    if (Objects.equals(System.getProperty("quarkus.native.enabled"), "true")) {
      command.add(System.getProperty("native.image.path"));
      command.addAll(kubeConfigArgs);
    } else {
      command.add(ProcessHandle.current().info().command().orElseThrow());
      command.addAll(kubeConfigArgs);
      command.add("-jar");
      command.add(System.getProperty("java.jar.path"));
    }
    final var transport = new StdioMcpTransport.Builder().command(command).logEvents(true).build();
    client = new DefaultMcpClient.Builder()
      .clientName("test-mcp-client-kubernetes")
      .toolExecutionTimeout(Duration.ofSeconds(10))
      .transport(transport)
      .build();
    // TODO: Remove once LangChain4J is fixed (1.0.0-alpha2)
    // https://github.com/langchain4j/langchain4j/pull/2360
    // https://github.com/langchain4j/langchain4j/issues/2341#issuecomment-2564081377
    final var execute = StdioMcpTransport.class.getDeclaredMethod("execute", String.class, Long.class);
    execute.setAccessible(true);
    execute.invoke(transport, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", 1000L);
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
    "pods_delete",
    "pods_list",
    "pods_list_in_namespace",
    "pods_run"
  })
  public void tools(String toolName) {
    assertThat(client.listTools())
      .extracting(ToolSpecification::name)
      .contains(toolName);
  }

  @Nested
  class NamespaceOperations {

    @Test
    void namespaces_list() {
      kubernetesClient.namespaces()
        .resource(new NamespaceBuilder().withNewMetadata().withName("a-namespace-to-list").endMetadata().build())
        .create();
      assertThat(client.executeTool(ToolExecutionRequest.builder().name("namespaces_list").arguments("{}").build()))
        .isNotBlank()
        .satisfies(nsList -> assertThat(kubernetesClient.getKubernetesSerialization().unmarshal(nsList, List.class))
          .extracting("metadata.name")
          .contains("a-namespace-to-list"));
    }
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
        .satisfies(pList -> assertThat(kubernetesClient.getKubernetesSerialization().unmarshal(pList, List.class))
          .extracting("metadata.name")
          .contains("a-pod-to-list"));
    }
  }

}
