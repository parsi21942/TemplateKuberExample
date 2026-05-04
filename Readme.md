@Value("${k8s.kubeconfig:}")
private String kubeconfigPath;

public K8sService() { } // убери создание клиента из конструктора

@PostConstruct
public void init() {
    Config config = kubeconfigPath.isBlank()
        ? Config.autoConfigure(null)
        : Config.fromKubeconfig(kubeconfigPath);

    this.client = new KubernetesClientBuilder()
            .withConfig(config)
            .build();
}
