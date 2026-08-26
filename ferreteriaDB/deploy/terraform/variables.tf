# ============================================================================
# variables.tf — Todos los knobs de escalamiento vertical/horizontal
# ============================================================================

variable "kubeconfig_path" {
  description = "Ruta al kubeconfig (vacío = ~/.kube/config)"
  type        = string
  default     = ""
}

variable "kubeconfig_context" {
  description = "Contexto de kubeconfig (vacío = actual)"
  type        = string
  default     = ""
}

variable "namespace" {
  type    = string
  default = "ferreteria"
}

variable "pg_image" {
  type    = string
  default = "postgres:17-alpine"
}

variable "pgbouncer_image" {
  type    = string
  default = "pgbouncer/pgbouncer:1.23.1"
}

variable "db_name" {
  type    = string
  default = "ferreteria"
}

variable "app_user" {
  type    = string
  default = "ferreteria_app"
}

# ---- Credenciales (SENSIBLES: pasar por TF_VAR_... o -var, nunca commit) ----
variable "admin_password" {
  type      = string
  sensitive = true
}

variable "app_password" {
  type      = string
  sensitive = true
}

variable "replication_password" {
  type      = string
  sensitive = true
}

# ---- Escalamiento VERTICAL de PostgreSQL ----
variable "pg_cpu_request" {
  type    = string
  default = "500m"
}
variable "pg_cpu_limit" {
  type    = string
  default = "2000m"
}
variable "pg_memory_request" {
  type    = string
  default = "1Gi"
}
variable "pg_memory_limit" {
  type    = string
  default = "4Gi"
}
variable "storage_class" {
  type    = string
  default = "" # vacío = default del clúster; usar SSD/NVMe (gp3, premium-rwo)
}
variable "storage_size" {
  type    = string
  default = "20Gi"
}

# ---- Escalamiento HORIZONTAL del pool de conexiones ----
variable "pgbouncer_replicas" {
  type    = number
  default = 2
}
variable "pgbouncer_hpa_min" {
  type    = number
  default = 2
}
variable "pgbouncer_hpa_max" {
  type    = number
  default = 6
}
variable "pgbouncer_hpa_cpu_target" {
  type    = number
  default = 70
}

# ---- Migración inicial ----
variable "load_demo_data" {
  description = "Incluir 05_dummy.sql (solo para ambientes demo)"
  type        = bool
  default     = true
}

variable "run_migration" {
  description = "Crear el Job que ejecuta los scripts SQL"
  type        = bool
  default     = true
}
