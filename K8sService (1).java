package com.example.k8sdashboard.service;

import com.example.k8sdashboard.dto.DeploymentInfo;
import io.fabric8.kubernetes.api.model.Namespace;
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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class K8sService {

    @Value("${k8s.kubeconfig}")
    private String kubeconfigPath;

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
    // Namespaces
    // -------------------------------------------------------

    /**
     * Получить список всех доступных namespace в кластере
     */
    public List<String> getAllNamespaces() {
        return client.namespaces()
                .list()
                .getItems()
                .stream()
                .map(ns -> ns.getMetadata().getName())
                .sorted()
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------
    // Deployments
    // -------------------------------------------------------

    /**
     * Все деплойменты в конкретном namespace
     */
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
     * Деплойменты во ВСЕХ namespace сразу
     */
    public List<DeploymentInfo> getAllDeployments() {
        return client.apps().deployments()
                .inAnyNamespace()
                .list()
                .getItems()
                .stream()
                .map(this::toDeploymentInfo)
                .collect(Collectors.toList());
    }

    /**
     * Один деплоймент по имени и namespace
     */
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
    // Actions — все принимают namespace явно
    // -------------------------------------------------------

    public void restart(String namespace, String name) {
        log.info("Restarting {}/{}", namespace, name);
        client.apps().deployments()
                .inNamespace(namespace)
                .withName(name)
                .rolling().restart();
    }

    public void stop(String namespace, String name) {
        log.info("Stopping {}/{}", namespace, name);
        client.apps().deployments()
                .inNamespace(namespace)
                .withName(name)
                .scale(0);
    }

    public void start(String namespace, String name) {
        log.info("Starting {}/{}", namespace, name);
        client.apps().deployments()
                .inNamespace(namespace)
                .withName(name)
                .scale(1);
    }

    public void scale(String namespace, String name, int replicas) {
        log.info("Scaling {}/{} to {} replicas", namespace, name, replicas);
        client.apps().deployments()
                .inNamespace(namespace)
                .withName(name)
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
                desired,
                ready,
                available,
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

    @PreDestroy
    public void close() {
        if (client != null) client.close();
    }
}
