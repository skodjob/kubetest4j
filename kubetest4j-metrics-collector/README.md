# MetricsCollector

The `MetricsCollector` is a utility class designed for gathering metrics from Kubernetes pods. 
It operates based on specific configurations such as Kubernetes component, namespace, and scraper pod, 
facilitating dynamic integration across different Kubernetes setups.

## Features

- **Dynamic Configuration**: Use the builder pattern to configure metrics collection tailored to various Kubernetes components.
- **Error Handling**: Robust exception handling to manage and respond to issues during metrics collection.
- **Versatile Data Collection**: Collects raw metrics data and processes it into structured formats like lists or maps.

## Prerequisites

- Java 17 or higher.
- Access to a Kubernetes cluster with configured pods.
- Kubernetes Client and Command Line Tools (kubectl) setup in your Java environment.

## Installation

You can add the following dependencies to your `pom.xml` if you are using Maven:

```xml
<dependencies>
    <dependency>
        <groupId>io.skodjob.kubetest4j</groupId>
        <artifactId>kubetest4j-metrics-collector</artifactId>
        <version>{version}</version>
    </dependency>
</dependencies>
```

## Usage

### Configuration

Create an instance of MetricsCollector using the builder pattern:

```java
MetricsCollector collector = new MetricsCollector.Builder()
    .withNamespaceName("default")
    .withScraperPodName("metrics-scraper")
    .withComponent(new MetricsComponent() {
        public int getDefaultMetricsPort() { return 8080; }
        public String getDefaultMetricsPath() { return "/metrics"; }
        public LabelSelector getLabelSelector() { return new LabelSelector(); }
    })
    .build();
```

The `DummyMetricsComponent` is an example implementation that defines the default port, path, and label selector for a Kubernetes component:
```java
public static class DummyMetricsComponent implements MetricsComponent {
    @Override
    public int getDefaultMetricsPort() {
        return 8080; // Default HTTP port
    }

    @Override
    public String getDefaultMetricsPath() {
        return "/metrics"; // Default endpoint for scraping metrics
    }

    @Override
    public LabelSelector getLabelSelector() {
        Map<String, String> matchLabels = new HashMap<>();
        matchLabels.put("app", "my-label-app"); // Example label selector for targeting specific pods
        return new LabelSelectorBuilder().withMatchLabels(matchLabels).build();
    }
}
```


### Collecting Metrics

To collect metrics from configured pods:
```java
try {
    collector.collectMetricsFromPods(30000); // timeout in milliseconds
    Map<String, List<Metric>> metrics = collector.getCollectedData();
    metrics.forEach((podName, metrics) -> {
        System.out.println(podName);
        metrics.forEach(metric -> System.out.println(metric.getName));
    });
} catch (MetricsCollectionException e) {
    System.err.println("Error collecting metrics: " + e.getMessage());
}
```

### Advanced Usage

Return all metrics with specific label present
```java
Map<String, List<Metric>> metrics = collector.collectMetricWithLabels("my-pod", "label");
```

This README provides a structured guide that helps users understand what the `MetricsCollector` does and how to implement it effectively in their projects.