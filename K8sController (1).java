package com.example.k8sdashboard.controller;

import com.example.k8sdashboard.dto.DeploymentInfo;
import com.example.k8sdashboard.service.K8sService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/k8s")
@RequiredArgsConstructor
public class K8sController {

    private final K8sService k8sService;

    // GET /api/k8s/namespaces
    @GetMapping("/namespaces")
    public ResponseEntity<List<String>> getNamespaces() {
        return ResponseEntity.ok(k8sService.getAllNamespaces());
    }

    // GET /api/k8s/deployments?ns=default
    // GET /api/k8s/deployments          (все namespace)
    @GetMapping("/deployments")
    public ResponseEntity<List<DeploymentInfo>> getDeployments(
            @RequestParam(required = false) String ns) {
        List<DeploymentInfo> result = (ns == null || ns.isBlank())
                ? k8sService.getAllDeployments()
                : k8sService.getDeployments(ns);
        return ResponseEntity.ok(result);
    }

    // GET /api/k8s/deployments/{name}?ns=default
    @GetMapping("/deployments/{name}")
    public ResponseEntity<DeploymentInfo> getDeployment(
            @PathVariable String name,
            @RequestParam String ns) {
        return ResponseEntity.ok(k8sService.getDeployment(ns, name));
    }

    // POST /api/k8s/deployments/{name}/restart
    // Body: { "ns": "default" }
    @PostMapping("/deployments/{name}/restart")
    public ResponseEntity<Map<String, String>> restart(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        String ns = body.get("ns");
        k8sService.restart(ns, name);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "action", "restart",
                "namespace", ns,
                "deployment", name
        ));
    }

    // POST /api/k8s/deployments/{name}/stop
    // Body: { "ns": "default" }
    @PostMapping("/deployments/{name}/stop")
    public ResponseEntity<Map<String, String>> stop(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        String ns = body.get("ns");
        k8sService.stop(ns, name);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "action", "stop",
                "namespace", ns,
                "deployment", name
        ));
    }

    // POST /api/k8s/deployments/{name}/start
    // Body: { "ns": "default" }
    @PostMapping("/deployments/{name}/start")
    public ResponseEntity<Map<String, String>> start(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        String ns = body.get("ns");
        k8sService.start(ns, name);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "action", "start",
                "namespace", ns,
                "deployment", name
        ));
    }

    // POST /api/k8s/deployments/{name}/scale
    // Body: { "ns": "default", "replicas": 3 }
    @PostMapping("/deployments/{name}/scale")
    public ResponseEntity<Map<String, Object>> scale(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        String ns = (String) body.get("ns");
        int replicas = (Integer) body.get("replicas");
        k8sService.scale(ns, name, replicas);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "action", "scale",
                "namespace", ns,
                "deployment", name,
                "replicas", replicas
        ));
    }

    // Обработка ошибок
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleError(Exception e) {
        log.error("API error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", e.getMessage()
        ));
    }
}
