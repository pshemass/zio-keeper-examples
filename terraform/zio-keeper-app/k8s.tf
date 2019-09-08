
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
            app = "${kubernetes_deployment.echo-deployment.metadata.0.labels.app}"
        }
        port {
            name = "zio-keeper"
            port        = 5558
        }

        type = "ClusterIP"
        cluster_ip = "None"
    }
}
resource "kubernetes_deployment" "echo-deployment" {
    metadata {
        name = "echo-deployment"
        namespace = "${kubernetes_namespace.zio-keeper-ns.metadata.0.name}"
        labels = {
            app = "echo-app"
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
                    "prometheus.io/scrape" =  'true'
                    "prometheus.io/port" = '9102'

                }
            }

            spec {
                container {
                    image = "rzbikson/zio-keeper-examples:0.1.4"
                    name  = "zio-keeper"
                    port {
                        container_port = 5558
                    }
                }
            }
        }
    }
}
