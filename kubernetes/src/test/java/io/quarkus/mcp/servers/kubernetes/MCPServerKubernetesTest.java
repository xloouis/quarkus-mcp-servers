package io.quarkus.mcp.servers.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void pods_list() {
      kubernetesClient
        .resource(new ServiceAccountBuilder().withNewMetadata().withName("default").endMetadata().build())
        .createOr(NonDeletingOperation::update);
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
      kubernetesClient
        .resource(new ServiceAccountBuilder().withNewMetadata().withName("default").endMetadata().build())
        .createOr(NonDeletingOperation::update);
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
      kubernetesClient
        .resource(new ServiceAccountBuilder().withNewMetadata().withName("default").endMetadata().build())
        .createOr(NonDeletingOperation::update);
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
      kubernetesClient
        .resource(new ServiceAccountBuilder().withNewMetadata().withName("default").endMetadata().build())
        .createOr(NonDeletingOperation::update);
      kubernetesClient.run()
        .withName("a-pod-to-delete")
        .withImage("busybox")
        .done();
      assertThat(server.pods_delete(null, "a-pod-to-delete"))
        .isEqualTo("Pod deleted successfully");
    }

    @Test
    void pods_log() {
      kubernetesClient
        .resource(new ServiceAccountBuilder().withNewMetadata().withName("default").endMetadata().build())
        .createOr(NonDeletingOperation::update);
      kubernetesClient.run()
        .withName("a-pod-to-log")
        .withImage("busybox")
        .done();
      assertThat(server.pods_log("default", "a-pod-to-log"))
        .isBlank();
    }

    @Test
    void pods_run_startsPod() {
      server.pods_run("default", "a-pod-to-run", "busybox");
      assertThat(kubernetesClient.pods().inNamespace("default").withName("a-pod-to-run")
        .waitUntilCondition(Objects::nonNull, 10, TimeUnit.SECONDS))
        .isNotNull()
        .hasFieldOrPropertyWithValue("metadata.name", "a-pod-to-run");
    }

    @Test
    void pods_run_returnsPodInfo() {
      assertThat(unmarshal(server.pods_run("default", "a-pod-to-run-2", "busybox"), Pod.class))
        .extracting("metadata.name")
        .isEqualTo("a-pod-to-run-2");
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
