# ============================================================================
# versions.tf — Proveedores requeridos
# ============================================================================
terraform {
  required_version = ">= 1.6.0"

  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
  }
}

provider "kubernetes" {
  # Usa el contexto actual de KUBECONFIG (minikube, kind, EKS, AKS, GKE...)
  config_path    = var.kubeconfig_path != "" ? var.kubeconfig_path : null
  config_context = var.kubeconfig_context != "" ? var.kubeconfig_context : null
}
