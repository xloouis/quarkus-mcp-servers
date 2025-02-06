package io.quarkus.mcp.servers.kubernetes;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MCPTestUtils {
  private MCPTestUtils() {
  }

  public static McpClient initMcpClient(String masterUrl) throws ReflectiveOperationException {
    final var kubeConfigArgs = List.of(
      "-Dquarkus.kubernetes-client.api-server-url=" + masterUrl,
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
    final var client = new DefaultMcpClient.Builder()
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
    return client;
  }
}
