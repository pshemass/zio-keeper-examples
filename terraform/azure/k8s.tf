resource "azurerm_resource_group" "k8s" {
    name     = "${var.resource_group_name}"
    location = "${var.location}"
}

resource "random_id" "log_analytics_workspace_name_suffix" {
    byte_length = 8
}

resource "azurerm_container_registry" "acr" {
  name                     = "pshemassAzurek8ACR"
  resource_group_name      = "${azurerm_resource_group.k8s.name}"
  location                 = "${azurerm_resource_group.k8s.location}"
  sku                      = "Basic"
  admin_enabled            = false
}

# resource "azurerm_role_assignment" "acrpullk8" {
#   scope                = "${azurerm_container_registry.acr.id}"
#   role_definition_name = "acrpull"
#   principal_id         = "${var.client_id}"
# }

resource "local_file" "kube_config" {
    content     = "${azurerm_kubernetes_cluster.k8s.kube_config_raw}"
    filename = "${pathexpand("~/.kube/config")}"
}

resource "azurerm_log_analytics_workspace" "test" {
    # The WorkSpace name has to be unique across the whole of azure, not just the current subscription/tenant.
    name                = "${var.log_analytics_workspace_name}-${random_id.log_analytics_workspace_name_suffix.dec}"
    location            = "${var.log_analytics_workspace_location}"
    resource_group_name = "${azurerm_resource_group.k8s.name}"
    sku                 = "${var.log_analytics_workspace_sku}"
}

resource "azurerm_log_analytics_solution" "test" {
    solution_name         = "ContainerInsights"
    location              = "${azurerm_log_analytics_workspace.test.location}"
    resource_group_name   = "${azurerm_resource_group.k8s.name}"
    workspace_resource_id = "${azurerm_log_analytics_workspace.test.id}"
    workspace_name        = "${azurerm_log_analytics_workspace.test.name}"

    plan {
        publisher = "Microsoft"
        product   = "OMSGallery/ContainerInsights"
    }
}

resource "azurerm_kubernetes_cluster" "k8s" {
    name                = "${var.cluster_name}"
    location            = "${azurerm_resource_group.k8s.location}"
    resource_group_name = "${azurerm_resource_group.k8s.name}"
    dns_prefix          = "${var.dns_prefix}"

    linux_profile {
        admin_username = "ubuntu"

        ssh_key {
            key_data = "${file("${var.ssh_public_key}")}"
        }
    }

    agent_pool_profile {
        name            = "agentpool"
        count           = "${var.agent_count}"
        vm_size         = "Standard_DS1_v2"
        os_type         = "Linux"
        os_disk_size_gb = 30
    }

    service_principal {
        client_id     = "${var.client_id}"
        client_secret = "${var.client_secret}"
    }

    addon_profile {
        oms_agent {
        enabled                    = true
        log_analytics_workspace_id = "${azurerm_log_analytics_workspace.test.id}"
        }
    }

    tags = {
        Environment = "Development"
    }
}



resource "kubernetes_namespace" "ingress-ns" {
    depends_on = [local_file.kube_config]
    metadata {
        name = "ingress-ns"
    }
}

resource "helm_release" "nginx-ingress" {
    name      = "nginx-ingress"
    chart     = "stable/nginx-ingress"
    namespace = "${kubernetes_namespace.ingress-ns.metadata.0.name}"

    set {
        name  = "controller.replicaCount"
        value = "2"
    }
}

data "helm_repository" "azure-samples" {
    name = "azure-samples"
    url = "https://azure-samples.github.io/helm-charts/"
}

resource "helm_release" "aks-hello-world1" {
    name      = "aks-hello-world1"
    chart     = "azure-samples/aks-helloworld"
    namespace = "${kubernetes_namespace.ingress-ns.metadata.0.name}"
}

resource "helm_release" "aks-hello-world2" {
    name      = "aks-hello-world2"
    chart     = "azure-samples/aks-helloworld"
    namespace = "${kubernetes_namespace.ingress-ns.metadata.0.name}"

    set {
        name  = "title"
        value = "AKS Ingress Demo"
    }

    set {
        name  = "serviceName"
        value = "ingress-demo"
    }
}

resource "kubernetes_ingress" "first-ingress" {
    metadata {
        name = "hello-world"
        namespace = "${kubernetes_namespace.ingress-ns.metadata.0.name}"
        annotations = {
            "kubernetes.io/ingress.class" = "nginx"
            "nginx.ingress.kubernetes.io/ssl-redirect" = "false"
            "nginx.ingress.kubernetes.io/rewrite-target" =  "/$1"
        }
    }
    spec {
        rule {
            http {
                path {
                    backend {
                        service_name = "aks-helloworld"
                        service_port = 80
                    }
                    path = "/(.*)"
                }
                path {
                    backend {
                        service_name = "ingress-demo"
                        service_port = 80
                    }
                    path = "/hello-world-two(/|$)(.*)"
                }
            }
        }
    }



}

