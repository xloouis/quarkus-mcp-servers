package io.quarkus.mcp.servers.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Basic unit test suite for the {@link MCPServerKubernetes} class.
 */
@QuarkusTest
@DisabledOnOs({OS.WINDOWS, OS.MAC})
class MCPServerKubernetesTest {

  @Inject
  KubernetesClient kubernetesClient;
  @Inject
  MCPServerKubernetes server;

  @Test
  void configuration_get_returnsTestKubernetesMasterUrl() {
    assertThat(unmarshal(server.configuration_get(), GenericKubernetesResource.class))
      .extracting(gkr -> gkr.get("masterUrl")).asString()
      .startsWith("https://localhost:");
  }

  @Nested
  class GenericResourceOperations {

    @Test
    void resources_list_clusterScopedWithIgnoredNamespace() {
      kubernetesClient.nodes()
        .resource(new NodeBuilder().withNewMetadata().withName("a-node-to-list").endMetadata().build())
        .serverSideApply();
      assertThat(unmarshalList(server.resources_list("v1", "Node", "ignored"), GenericKubernetesResource.class))
        .extracting("kind", "metadata.name")
        .contains(tuple("Node", "a-node-to-list"));
    }

    @Test
    void resources_list_namespaceScopedAllNamespaces() {
      kubernetesClient.namespaces()
        .resource(new NamespaceBuilder().withNewMetadata().withName("other-namespace").endMetadata().build())
        .serverSideApply();
      kubernetesClient.configMaps()
        .resource(new ConfigMapBuilder().withNewMetadata().withName("a-configmap-to-list").endMetadata().build())
        .serverSideApply();
      kubernetesClient.configMaps()
        .inNamespace("other-namespace")
        .resource(new ConfigMapBuilder().withNewMetadata().withName("a-configmap-to-list-in-other-namespace").endMetadata().build())
        .serverSideApply();
      assertThat(unmarshalList(server.resources_list("v1", "ConfigMap", null), GenericKubernetesResource.class))
        .extracting("kind", "metadata.namespace", "metadata.name")
        .contains(
          tuple("ConfigMap", "default", "a-configmap-to-list"),
          tuple("ConfigMap", "other-namespace", "a-configmap-to-list-in-other-namespace")
        );
    }

    @Test
    void resources_get_clusterScopedWithIgnoredNamespace() {
      kubernetesClient.nodes()
        .resource(new NodeBuilder().withNewMetadata().withName("a-node-to-get").endMetadata().build())
        .serverSideApply();
      assertThat(unmarshal(server.resources_get("v1", "Node", "ignored", "a-node-to-get"), Node.class))
        .hasFieldOrPropertyWithValue("metadata.name", "a-node-to-get");
    }

    @Test
    void resources_get_namespaceScoped() {
      kubernetesClient.configMaps()
        .resource(new ConfigMapBuilder().withNewMetadata().withName("a-configmap-to-get").endMetadata().build())
        .serverSideApply();
      assertThat(unmarshal(server.resources_get("v1", "ConfigMap", "default", "a-configmap-to-get"), ConfigMap.class))
        .hasFieldOrPropertyWithValue("metadata.name", "a-configmap-to-get");
    }

    @Test
    void resources_create_or_update_clusterScopedWithIgnoredNamespace() {
      assertThat(server.resources_create_or_update("{\"apiVersion\":\"v1\",\"kind\":\"Node\",\"metadata\":{\"name\":\"a-node-to-create\"}}"))
        .startsWith("{\"apiVersion\":\"v1\",\"kind\":\"Node\",\"metadata\":{");
      assertThat(kubernetesClient.nodes().withName("a-node-to-create").get())
        .hasFieldOrPropertyWithValue("metadata.name", "a-node-to-create");
    }

    @Test
    void resources_create_or_update_namespaceScoped() {
      assertThat(server.resources_create_or_update("{\"apiVersion\":\"v1\",\"kind\":\"ConfigMap\",\"metadata\":{\"name\":\"a-configmap-to-create\"}}"))
        .startsWith("{\"apiVersion\":\"v1\",\"kind\":\"ConfigMap\",\"metadata\":{");
      assertThat(kubernetesClient.configMaps().inNamespace("default").withName("a-configmap-to-create").get())
        .hasFieldOrPropertyWithValue("metadata.name", "a-configmap-to-create");
    }

    @Test
    void resources_delete_clusterScopedWithIgnoredNamespace() {
      kubernetesClient.nodes()
        .resource(new NodeBuilder().withNewMetadata().withName("a-node-to-delete").endMetadata().build())
        .serverSideApply();
      assertThat(server.resources_delete("v1", "Node", "ignored", "a-node-to-delete"))
        .isEqualTo("Resource deleted successfully");
    }

    @Test
    void resources_delete_namespaceScoped() {
      kubernetesClient.configMaps()
        .resource(new ConfigMapBuilder().withNewMetadata().withName("a-configmap-to-delete").endMetadata().build())
        .serverSideApply();
      assertThat(server.resources_delete("v1", "ConfigMap", "default", "a-configmap-to-delete"))
        .isEqualTo("Resource deleted successfully");
    }
  }

  @Test
  void namespaces_list() {
    kubernetesClient.namespaces()
      .resource(new NamespaceBuilder().withNewMetadata().withName("a-namespace-to-list").endMetadata().build())
      .serverSideApply();
    assertThat(unmarshalList(server.namespaces_list(), Namespace.class))
      .extracting("metadata.name")
      .contains("a-namespace-to-list");
  }

  @Nested
  class PodOperations {

    @BeforeEach
    void setRequiredServiceAccount() {
      kubernetesClient
        .resource(new ServiceAccountBuilder().withNewMetadata().withName("default").endMetadata().build())
        .createOr(NonDeletingOperation::update);
    }

    @Test
    void pods_list() {
      kubernetesClient.run()
        .withName("a-pod-to-list")
        .withImage("busybox")
        .done();
      assertThat(unmarshalList(server.pods_list(), Pod.class))
        .extracting("metadata.name")
        .contains("a-pod-to-list");
    }

    @Test
    void pods_list_in_namespace() {
      kubernetesClient.run()
        .withName("a-pod-to-list-in-namespace")
        .withImage("busybox")
        .done();
      assertThat(unmarshalList(server.pods_list_in_namespace("default"), Pod.class))
        .extracting("metadata.name")
        .contains("a-pod-to-list-in-namespace");
    }

    @Test
    void pods_get() {
      kubernetesClient.run()
        .withName("a-pod-to-get")
        .withImage("busybox")
        .done();
      assertThat(unmarshal(server.pods_get(null, "a-pod-to-get"), Pod.class))
        .extracting("metadata.name")
        .isEqualTo("a-pod-to-get");
    }

    @Test
    void pods_delete() {
      kubernetesClient.run()
        .withName("a-pod-to-delete")
        .withImage("busybox")
        .done();
      assertThat(server.pods_delete(null, "a-pod-to-delete"))
        .isEqualTo("Pod deleted successfully");
    }

    @Test
    void pods_log() {
      kubernetesClient.run()
        .withName("a-pod-to-log")
        .withImage("busybox")
        .done();
      assertThat(server.pods_log("default", "a-pod-to-log"))
        .isBlank();
    }

    @Test
    void pods_run_startsPod() {
      server.pods_run("default", "a-pod-to-run", "busybox", null);
      assertThat(kubernetesClient.pods().inNamespace("default").withName("a-pod-to-run")
        .waitUntilCondition(Objects::nonNull, 10, TimeUnit.SECONDS))
        .isNotNull()
        .hasFieldOrPropertyWithValue("metadata.name", "a-pod-to-run");
    }

    @Test
    void pods_run_returnsPodInfo() {
      assertThat(unmarshalList(server.pods_run("default", "a-pod-to-run-2", "busybox", null), Pod.class))
        .extracting("kind", "metadata.name")
        .contains(
          tuple("Pod", "a-pod-to-run-2")
        );
    }

    @Test
    void pods_run_returnsServiceInfo() {
      assertThat(unmarshalList(server.pods_run("default", "a-pod-to-run-with-service", "busybox", 8080), GenericKubernetesResource.class))
        .extracting("kind", "metadata.name")
        .contains(
          tuple("Pod", "a-pod-to-run-with-service"),
          tuple("Service", "a-pod-to-run-with-service")
        );
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
