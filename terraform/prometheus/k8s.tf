

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
    metadata {
        name = "prometheus-server"
        namespace = "${kubernetes_namespace.monitoring-ns.metadata.0.name}"
    }
}

resource "helm_release" "grafana" {
    name      = "grafana"
    chart     = "stable/grafana"
    namespace = "${kubernetes_namespace.monitoring-ns.metadata.0.name}"

    set {
        name  = "persistence.enabled"
        value = true
    }

    set {
        name  = "persistence.accessModes"
        value = "{ReadWriteOnce}"
    }

    set {
        name  = "persistence.size"
        value = "8Gi"
    }

    set {
        name = "dataSources"
        value = "${file("${path.module}/datasources.yml")}"
    }
}


