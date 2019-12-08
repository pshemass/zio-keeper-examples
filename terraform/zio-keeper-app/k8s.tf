
resource "kubernetes_namespace" "zio-keeper-ns" {
    metadata {
        name = "zio-keeper"
    }
}

resource "kubernetes_service" "echo-srv" {
    metadata {
        name = "echo-srv"
        namespace = "${kubernetes_namespace.zio-keeper-ns.metadata.0.name}"
    }
    spec {
        selector = {
            app = "echo-app"
        }
        port {
            name = "zio-keeper-examples"
            port        = 5558
        }

        type = "ClusterIP"
        cluster_ip = "None"
    }
}
resource "kubernetes_deployment" "echo-deployment" {
    depends_on = [kubernetes_service.echo-srv]
    metadata {
        name = "echo-deployment"
        namespace = "${kubernetes_namespace.zio-keeper-ns.metadata.0.name}"
        labels = {
            app = "${kubernetes_service.echo-srv.spec.0.selector.app}"
        }
    }

    spec {
        replicas = 3

        selector {
            match_labels = {
                app = "echo-app"
            }
        }

        template {
            metadata {
                labels = {
                    app = "echo-app"
                }
                annotations = {
                    "prometheus.io/scrape" = "true"
                    "prometheus.io/port" = "9090"

                }
            }

            spec {
                container {
                    image = "rzbikson/zio-keeper-examples:0.1.6"
                    name  = "zio-keeper-examples"
                    port {
                        container_port = 5558
                    }
                    port {
                        container_port = 9090
                    }
                }
            }
        }
    }
}
