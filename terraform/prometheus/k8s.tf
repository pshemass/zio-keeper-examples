

resource "kubernetes_namespace" "monitoring-ns" {
    metadata {
        name = "monitoring-ns"
    }
}

resource "helm_release" "prometheus" {
    name      = "prometheus"
    chart     = "stable/prometheus"
    namespace = "${kubernetes_namespace.monitoring-ns.metadata.0.name}"

    set {
        name  = "rbac.create"
        value = true
    }
}

data "kubernetes_service" "prometheus-server" {
    depends_on = [helm_release.prometheus]
    metadata {
        name = "prometheus-server"
        namespace = "${kubernetes_namespace.monitoring-ns.metadata.0.name}"
    }
}

resource "helm_release" "grafana" {
    name      = "grafana"
    chart     = "stable/grafana"
    namespace = "${kubernetes_namespace.monitoring-ns.metadata.0.name}"

    values = [
<<EOF
apiVersion: 1
persistence:
  enabled: true
  accessModes:
    - ReadWriteOnce
  size: 8Gi
datasources:
  apiVersion: 1
  datasources.yaml:
    - name: Prometheus
      type: prometheus
      url: http://prometheus-server
      access: proxy
      isDefault: true
EOF
    ]
}


