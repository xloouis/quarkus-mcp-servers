///usr/bin/env jbang "$0" "$@" ; exit $?
package io.quarkus.mcp.servers.kubernetes;
//JAVA 17+
//DEPS io.quarkus:quarkus-bom:3.17.6@pom
//DEPS io.quarkus:quarkus-kubernetes-client
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.0.0.Alpha5

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkiverse.mcp.server.ToolCallException;


import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class MCPServerKubernetes {

  @Inject
  KubernetesClient kubernetesClient;

  @Startup
  void init() {
    Log.info("Starting Kubernetes server with master URL: " + kubernetesClient.getConfiguration().getMasterUrl());
  }

  @Tool(description = "Get the current Kubernetes configuration")
  public String configuration_get() {
    try {
      return kubernetesClient.getKubernetesSerialization().asJson(kubernetesClient.getConfiguration());
    } catch (Exception e) {
      throw new ToolCallException("Failed to get configuration: " + e.getMessage(), e);
    }
  }

  @Tool(description = "List all the Kubernetes namespaces in the current cluster")
  public String namespaces_list() {
    try {
      return kubernetesClient.getKubernetesSerialization().asJson(
        kubernetesClient.namespaces().list().getItems()
      );
    } catch (Exception e) {
      throw new ToolCallException("Failed to list namespaces: " + e.getMessage(), e);
    }
  }

  @Tool(description = "List all the Kubernetes pods in the current cluster")
  public String pods_list() {
    try {
      return kubernetesClient.getKubernetesSerialization().asJson(
        kubernetesClient.pods().list().getItems()
      );
    } catch (Exception e) {
      throw new ToolCallException("Failed to list pods: " + e.getMessage(), e);
    }
  }

  @Tool(description = "List all the Kubernetes pods in the specified namespace in the current cluster")
  public String pods_list_in_namespace(@ToolArg(description = "Namespace to list pods from") String namespace) {
    try {
      return kubernetesClient.getKubernetesSerialization().asJson(
        kubernetesClient.pods().inNamespace(namespace).list().getItems()
      );
    } catch (Exception e) {
      throw new ToolCallException("Failed to list pods in namespace: " + e.getMessage(), e);
    }
  }

  @Tool(description = "Run a Pod in the current namespace with the provided container image and optional name")
  public String pods_run(
    @ToolArg(description = "Namespace to run the Pod in", required = false) String namespace,
    @ToolArg(description = "Container Image to run in the Pod") String image,
    @ToolArg(description = "Name of the Pod", required = false) String name
  ) {
    try {
      return kubernetesClient.getKubernetesSerialization().asJson(
        kubernetesClient.run()
          .inNamespace(namespace == null ? kubernetesClient.getNamespace() : namespace)
          .withName(name == null ? "mcp-kubernetes-pod-" + System.currentTimeMillis() : name)
          .withImage(image)
          .withNewRunConfig()
          .addToLabels("app", "mcp-kubernetes")
          .addToLabels("k8s.io/created-by", "mcp-kubernetes")
          .done()
      );
    } catch (Exception e) {
      throw new ToolCallException("Failed to run pod: " + e.getMessage(), e);
    }
  }

  @Tool(description = "Delete a Pod in the current namespace with the provided name")
  public String pods_delete(
    @ToolArg(description = "Namespace to delete the Pod from", required = false) String namespace,
    @ToolArg(description = "Name of the Pod to delete") String name
  ) {
    try {
      kubernetesClient.pods()
        .inNamespace(namespace == null ? kubernetesClient.getNamespace() : namespace)
        .withName(name)
        .withTimeout(10, TimeUnit.SECONDS)
        .delete();
    } catch (Exception e) {
      throw new ToolCallException("Failed to delete Pod: " + e.getMessage(), e);
    }
    return "Pod deleted successfully";
  }
}
