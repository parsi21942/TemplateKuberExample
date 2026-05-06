        package com.example.k8sdashboard.service;

import com.example.k8sdashboard.dto.DeploymentInfo;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class K8sService {

    @Value("${k8s.kubeconfig}")
    private String kubeconfigPath;

    // Fallback: если нет прав на автоматическое получение namespace
    @Value("${k8s.namespaces.fallback:default}")
    private String namespacesFallback;

    private KubernetesClient client;

    @PostConstruct
    public void init() {
        try {
            log.info("Loading kubeconfig from: {}", kubeconfigPath);
            String kubeconfigContent = Files.readString(Paths.get(kubeconfigPath));
            Config config = Config.fromKubeconfig(kubeconfigContent);
            this.client = new KubernetesClientBuilder()
                    .withConfig(config)
                    .build();
            log.info("Kubernetes client initialized. Master URL: {}", client.getMasterUrl());
        } catch (Exception e) {
            log.error("Failed to initialize Kubernetes client", e);
            throw new RuntimeException("Cannot load kubeconfig from: " + kubeconfigPath, e);
        }
    }

    // -------------------------------------------------------
    // Namespaces — hybrid: авто если есть права, иначе fallback
    // -------------------------------------------------------

    public List<String> getAllNamespaces() {
        try {
            List<String> namespaces = client.namespaces()
                    .list()
                    .getItems()
                    .stream()
                    .map(ns -> ns.getMetadata().getName())
                    .sorted()
                    .collect(Collectors.toList());

            log.info("Auto-discovered {} namespaces: {}", namespaces.size(), namespaces);
            return namespaces;

        } catch (Exception e) {
            List<String> fallback = parseFallback();
            log.warn("Cannot list namespaces automatically ({}). Using fallback from config: {}",
                    e.getMessage(), fallback);
            return fallback;
        }
    }

    // -------------------------------------------------------
    // Deployments
    // -------------------------------------------------------

    public List<DeploymentInfo> getDeployments(String namespace) {
        return client.apps().deployments()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .map(this::toDeploymentInfo)
                .collect(Collectors.toList());
    }

    /**
     * Деплойменты из всех namespace.
     * Пробуем inAnyNamespace() — если нет прав, итерируемся по fallback-списку.
     */
    public List<DeploymentInfo> getAllDeployments() {
        try {
            List<DeploymentInfo> result = client.apps().deployments()
                    .inAnyNamespace()
                    .list()
                    .getItems()
                    .stream()
                    .map(this::toDeploymentInfo)
                    .collect(Collectors.toList());

            log.info("Loaded {} deployments via inAnyNamespace()", result.size());
            return result;

        } catch (Exception e) {
            log.warn("Cannot list all deployments at once ({}). Iterating per namespace.", e.getMessage());

            return getAllNamespaces().stream()
                    .flatMap(ns -> {
                        try {
                            return getDeployments(ns).stream();
                        } catch (Exception nsEx) {
                            log.warn("Skipping namespace '{}' — no access: {}", ns, nsEx.getMessage());
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    public DeploymentInfo getDeployment(String namespace, String name) {
        Deployment dep = client.apps().deployments()
                .inNamespace(namespace)
                .withName(name)
                .get();
        if (dep == null) {
            throw new RuntimeException("Deployment not found: " + namespace + "/" + name);
        }
        return toDeploymentInfo(dep);
    }

    // -------------------------------------------------------
    // Actions
    // -------------------------------------------------------

    public void restart(String namespace, String name) {
        log.info("Restarting {}/{}", namespace, name);
        client.apps().deployments()
                .inNamespace(namespace).withName(name)
                .rolling().restart();
    }

    public void stop(String namespace, String name) {
        log.info("Stopping {}/{}", namespace, name);
        client.apps().deployments()
                .inNamespace(namespace).withName(name)
                .scale(0);
    }

    public void start(String namespace, String name) {
        log.info("Starting {}/{}", namespace, name);
        client.apps().deployments()
                .inNamespace(namespace).withName(name)
                .scale(1);
    }

    public void scale(String namespace, String name, int replicas) {
        log.info("Scaling {}/{} to {} replicas", namespace, name, replicas);
        client.apps().deployments()
                .inNamespace(namespace).withName(name)
                .scale(replicas);
    }

    // -------------------------------------------------------
    // Маппинг
    // -------------------------------------------------------

    private DeploymentInfo toDeploymentInfo(Deployment dep) {
        DeploymentStatus st = dep.getStatus();
        int desired   = getOrZero(dep.getSpec().getReplicas());
        int ready     = getOrZero(st.getReadyReplicas());
        int available = getOrZero(st.getAvailableReplicas());

        return new DeploymentInfo(
                dep.getMetadata().getName(),
                dep.getMetadata().getNamespace(),
                desired, ready, available,
                resolveStatus(desired, ready)
        );
    }

    private String resolveStatus(int desired, int ready) {
        if (desired == 0)    return "STOPPED";
        if (ready == 0)      return "STOPPED";
        if (ready < desired) return "PARTIAL";
        return "RUNNING";
    }

    private int getOrZero(Integer v) {
        return v != null ? v : 0;
    }

    private List<String> parseFallback() {
        return Arrays.stream(namespacesFallback.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    @PreDestroy
    public void close() {
        if (client != null) client.close();
    }
}
