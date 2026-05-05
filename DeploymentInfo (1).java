package com.example.k8sdashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeploymentInfo {
    private String name;
    private String namespace;
    private int replicas;
    private int readyReplicas;
    private int availableReplicas;
    private String status; // RUNNING, STOPPED, PARTIAL, UNKNOWN
}
