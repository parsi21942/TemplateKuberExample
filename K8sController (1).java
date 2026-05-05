package com.example.k8sdashboard.controller;

import com.example.k8sdashboard.dto.DeploymentInfo;
import com.example.k8sdashboard.service.K8sService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class K8sController {

    private final K8sService k8sService;

    @Value("${k8s.namespace.default:default}")
    private String defaultNamespace;

    /**
     * Главная страница.
     * ?ns=my-namespace  — показать конкретный namespace
     * ?ns=__all__       — показать все namespace сразу
     */
    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String ns, Model model) {
        // Если ns не передан — берём дефолтный
        if (ns == null || ns.isBlank()) {
            ns = defaultNamespace;
        }

        try {
            // Список всех namespace для табов
            List<String> namespaces = k8sService.getAllNamespaces();
            model.addAttribute("namespaces", namespaces);
            model.addAttribute("currentNs", ns);

            // Деплойменты
            List<DeploymentInfo> deployments = ns.equals("__all__")
                    ? k8sService.getAllDeployments()
                    : k8sService.getDeployments(ns);

            model.addAttribute("deployments", deployments);
            model.addAttribute("showAll", ns.equals("__all__"));

        } catch (Exception e) {
            log.error("Failed to load dashboard", e);
            model.addAttribute("error", "Ошибка: " + e.getMessage());
        }

        return "dashboard";
    }

    // ---- Actions — namespace передаётся как @RequestParam ----

    @PostMapping("/restart/{name}")
    public String restart(@PathVariable String name,
                          @RequestParam String ns,
                          RedirectAttributes ra) {
        try {
            k8sService.restart(ns, name);
            ra.addFlashAttribute("success", "[" + ns + "] «" + name + "» перезапущен ✓");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка перезапуска: " + e.getMessage());
        }
        return "redirect:/?ns=" + ns;
    }

    @PostMapping("/stop/{name}")
    public String stop(@PathVariable String name,
                       @RequestParam String ns,
                       RedirectAttributes ra) {
        try {
            k8sService.stop(ns, name);
            ra.addFlashAttribute("success", "[" + ns + "] «" + name + "» остановлен ✓");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка остановки: " + e.getMessage());
        }
        return "redirect:/?ns=" + ns;
    }

    @PostMapping("/start/{name}")
    public String start(@PathVariable String name,
                        @RequestParam String ns,
                        RedirectAttributes ra) {
        try {
            k8sService.start(ns, name);
            ra.addFlashAttribute("success", "[" + ns + "] «" + name + "» запущен ✓");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка запуска: " + e.getMessage());
        }
        return "redirect:/?ns=" + ns;
    }

    @PostMapping("/scale/{name}")
    public String scale(@PathVariable String name,
                        @RequestParam String ns,
                        @RequestParam int replicas,
                        RedirectAttributes ra) {
        try {
            k8sService.scale(ns, name, replicas);
            ra.addFlashAttribute("success",
                    "[" + ns + "] «" + name + "» → " + replicas + " реплик ✓");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка масштабирования: " + e.getMessage());
        }
        return "redirect:/?ns=" + ns;
    }

    // ---- REST API ----

    @GetMapping("/api/namespaces")
    @ResponseBody
    public List<String> namespacesApi() {
        return k8sService.getAllNamespaces();
    }

    @GetMapping("/api/deployments")
    @ResponseBody
    public List<DeploymentInfo> deploymentsApi(
            @RequestParam(defaultValue = "default") String ns) {
        return ns.equals("__all__")
                ? k8sService.getAllDeployments()
                : k8sService.getDeployments(ns);
    }
}
