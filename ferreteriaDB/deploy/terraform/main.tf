# ============================================================================
# main.tf — Stack completo de base de datos Ferretería en Kubernetes
# Namespace + Secret + ConfigMaps + StatefulSet PostgreSQL + PgBouncer
# (Deployment + HPA + PDB) + NetworkPolicies + Job de migración.
# ============================================================================

locals {
  labels = {
    "app.kubernetes.io/part-of"  = "ferreteria"
    "app.kubernetes.io/managed-by" = "terraform"
  }

  # Scripts SQL gestionados como ConfigMap (orden lexicográfico al ejecutar)
  sql_files_base = [
    "01_base_esquemas.sql",
    "02_tablas.sql",
    "vistas_core.sql",
    "03_parametria.sql",
    "04_admin.sql",
  ]
  sql_files = var.load_demo_data ? concat(local.sql_files_base, ["05_dummy.sql"]) : local.sql_files_base

  sql_configmap = {
    for f in local.sql_files : f => file("${path.module}/../scripts/${f}")
  }
}

# ----------------------------------------------------------------------------
# Namespace y Secret
# ----------------------------------------------------------------------------
resource "kubernetes_namespace_v1" "this" {
  metadata {
    name   = var.namespace
    labels = local.labels
  }
}

resource "kubernetes_secret_v1" "db" {
  metadata {
    name      = "ferreteria-db-secret"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }
  type = "Opaque"
  data = {
    POSTGRES_PASSWORD     = var.admin_password
    POSTGRES_APP_PASSWORD = var.app_password
    REPLICATION_PASSWORD  = var.replication_password
  }
}

# ----------------------------------------------------------------------------
# ConfigMap de tuning de PostgreSQL (mismo conf que docker-compose)
# ----------------------------------------------------------------------------
resource "kubernetes_config_map_v1" "pg_conf" {
  metadata {
    name      = "ferreteria-postgres-conf"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }
  data = {
    "postgresql.conf" = file("${path.module}/../conf/postgresql.conf")
    "pg_hba.conf"     = file("${path.module}/../conf/pg_hba.conf")
  }
}

# ----------------------------------------------------------------------------
# ConfigMap con los scripts SQL de inicialización
# ----------------------------------------------------------------------------
resource "kubernetes_config_map_v1" "sql_scripts" {
  metadata {
    name      = "ferreteria-sql"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }
  data = local.sql_configmap
}

# ----------------------------------------------------------------------------
# Services: headless para el StatefulSet + ClusterIP estable
# ----------------------------------------------------------------------------
resource "kubernetes_service_v1" "headless" {
  metadata {
    name      = "postgres-headless"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }
  spec {
    cluster_ip                   = "None"
    publish_not_ready_addresses  = true
    selector = { app = "postgres" }
    port {
      name     = "pg"
      port     = 5432
      protocol = "TCP"
    }
  }
}

resource "kubernetes_service_v1" "rw" {
  metadata {
    name      = "postgres-rw"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }
  spec {
    selector = { app = "postgres" }
    port {
      name       = "pg"
      port       = 5432
      target_port = "5432"
      protocol   = "TCP"
    }
  }
}

# ----------------------------------------------------------------------------
# StatefulSet de PostgreSQL (escalamiento vertical vía variables)
# ----------------------------------------------------------------------------
resource "kubernetes_stateful_set_v1" "postgres" {
  metadata {
    name      = "postgres"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  spec {
    service_name           = kubernetes_service_v1.headless.metadata[0].name
    replicas               = 1          # single-writer; HA avanzada → CloudNativePG
    pod_management_policy  = "OrderedReady"

    selector {
      match_labels = { app = "postgres" }
    }

    template {
      metadata {
        labels = {
          app  = "postgres"
          tier = "database"
        }
      }

      spec {
        termination_grace_period_seconds = 60

        security_context {
          fs_group                = 999
          run_as_user             = 999
          run_as_group            = 999
          fs_group_change_policy  = "OnRootMismatch"
        }

        container {
          name              = "postgres"
          image             = var.pg_image
          image_pull_policy = "IfNotPresent"
          args              = ["-c", "config_file=/etc/postgresql/postgresql.conf"]

          env {
            name  = "POSTGRES_USER"
            value = "postgres"
          }
          env {
            name  = "POSTGRES_DB"
            value = var.db_name
          }
          env {
            name  = "TZ"
            value = "America/Mexico_City"
          }
          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.db.metadata[0].name
                key  = "POSTGRES_PASSWORD"
              }
            }
          }

          port {
            container_port = 5432
            name           = "pg"
          }

          resources {
            requests = {
              cpu    = var.pg_cpu_request
              memory = var.pg_memory_request
            }
            limits = {
              cpu    = var.pg_cpu_limit
              memory = var.pg_memory_limit
            }
          }

          volume_mount {
            name       = "data"
            mount_path = "/var/lib/postgresql/data"
          }
          volume_mount {
            name       = "conf"
            mount_path = "/etc/postgresql"
          }

          startup_probe {
            exec {
              command = ["pg_isready", "-U", "postgres", "-d", var.db_name, "-q"]
            }
            period_seconds    = 10
            failure_threshold = 30
          }
          liveness_probe {
            exec {
              command = ["pg_isready", "-U", "postgres", "-d", var.db_name, "-q"]
            }
            period_seconds  = 15
            timeout_seconds = 5
          }
          readiness_probe {
            exec {
              command = ["sh", "-c", "pg_isready -U postgres -d ${var.db_name} -q"]
            }
            period_seconds = 10
          }
        }

        volume {
          name = "conf"
          config_map {
            name = kubernetes_config_map_v1.pg_conf.metadata[0].name
          }
        }
      }
    }

    volume_claim_templates {
      metadata {
        name = "data"
      }
      spec {
        access_modes       = ["ReadWriteOnce"]
        storage_class_name = var.storage_class == "" ? null : var.storage_class
        resources {
          requests = {
            storage = var.storage_size
          }
        }
      }
    }
  }

  depends_on = [kubernetes_secret_v1.db, kubernetes_config_map_v1.pg_conf]
}

# ----------------------------------------------------------------------------
# PgBouncer — Deployment escalable horizontalmente
# ----------------------------------------------------------------------------
resource "kubernetes_deployment_v1" "pgbouncer" {
  metadata {
    name      = "pgbouncer"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = merge(local.labels, { app = "pgbouncer" })
  }

  spec {
    replicas = var.pgbouncer_replicas

    strategy {
      type = "RollingUpdate"
      rolling_update {
        max_surge       = 1
        max_unavailable = 0
      }
    }

    selector {
      match_labels = { app = "pgbouncer" }
    }

    template {
      metadata {
        labels = { app = "pgbouncer", tier = "db-proxy" }
      }

      spec {
        container {
          name              = "pgbouncer"
          image             = var.pgbouncer_image
          image_pull_policy = "IfNotPresent"

          env {
            name  = "DB_HOST"
            value = "${kubernetes_service_v1.rw.metadata[0].name}.${kubernetes_namespace_v1.this.metadata[0].name}.svc.cluster.local"
          }
          env { name = "DB_PORT";  value = "5432" }
          env { name = "DB_NAME";  value = var.db_name }
          env { name = "DB_USER";  value = var.app_user }
          env {
            name = "DB_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.db.metadata[0].name
                key  = "POSTGRES_APP_PASSWORD"
              }
            }
          }
          env { name = "AUTH_TYPE";           value = "scram-sha-256" }
          env { name = "POOL_MODE";           value = "transaction" }
          env { name = "LISTEN_ADDR";         value = "0.0.0.0" }
          env { name = "LISTEN_PORT";         value = "6432" }
          env { name = "MAX_CLIENT_CONN";     value = "1000" }
          env { name = "DEFAULT_POOL_SIZE";   value = "50" }
          env { name = "MIN_POOL_SIZE";       value = "10" }
          env { name = "RESERVE_POOL_SIZE";   value = "10" }
          env { name = "MAX_DB_CONNECTIONS";  value = "120" }

          port {
            container_port = 6432
            name           = "bouncer"
          }

          resources {
            requests = { cpu = "100m", memory = "128Mi" }
            limits   = { cpu = "500m", memory = "512Mi" }
          }

          readiness_probe {
            tcp_socket { port = 6432 }
            period_seconds = 10
          }
          liveness_probe {
            tcp_socket { port = 6432 }
            initial_delay_seconds = 10
            period_seconds        = 15
          }
        }
      }
    }
  }

  depends_on = [kubernetes_stateful_set_v1.postgres]
}

resource "kubernetes_service_v1" "pgbouncer" {
  metadata {
    name      = "pgbouncer"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }
  spec {
    selector = { app = "pgbouncer" }
    port {
      name        = "bouncer"
      port        = 6432
      target_port = "6432"
      protocol    = "TCP"
    }
  }
}

# HPA v1 clásico (estable en todas las versiones del provider): CPU objetivo
resource "kubernetes_horizontal_pod_autoscaler" "pgbouncer" {
  metadata {
    name      = "pgbouncer-hpa"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }
  spec {
    min_replicas                    = var.pgbouncer_hpa_min
    max_replicas                    = var.pgbouncer_hpa_max
    target_cpu_utilization_percentage = var.pgbouncer_hpa_cpu_target

    scale_target_ref {
      api_version = "apps/v1"
      kind        = "Deployment"
      name        = kubernetes_deployment_v1.pgbouncer.metadata[0].name
    }
  }
}

resource "kubernetes_pod_disruption_budget_v1" "pgbouncer" {
  metadata {
    name      = "pgbouncer-pdb"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }
  spec {
    min_available = 1
    selector {
      match_labels = { app = "pgbouncer" }
    }
  }
}

# ----------------------------------------------------------------------------
# NetworkPolicy: solo PgBouncer → PostgreSQL; apps del namespace → PgBouncer
# ----------------------------------------------------------------------------
resource "kubernetes_network_policy" "postgres_ingress" {
  metadata {
    name      = "postgres-default-deny"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }

  spec {
    pod_selector {
      match_labels = { app = "postgres" }
    }
    policy_types = ["Ingress"]

    ingress {
      from {
        pod_selector {
          match_labels = { app = "pgbouncer" }
        }
      }
      port {
        protocol = "TCP"
        port     = "5432"
      }
    }
  }
}

resource "kubernetes_network_policy" "pgbouncer_ingress" {
  metadata {
    name      = "pgbouncer-ingress"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
  }

  spec {
    pod_selector {
      match_labels = { app = "pgbouncer" }
    }
    policy_types = ["Ingress"]

    ingress {
      from {
        pod_selector {}   # cualquier pod del namespace (backend Java)
      }
      port {
        protocol = "TCP"
        port     = "6432"
      }
    }
  }
}

# ----------------------------------------------------------------------------
# Job de migración inicial (ejecuta scripts SQL contra el primario)
# ----------------------------------------------------------------------------
resource "kubernetes_job_v1" "migration" {
  count = var.run_migration ? 1 : 0

  metadata {
    name      = "ferreteria-db-init"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  spec {
    backoff_limit            = 2
    ttl_seconds_after_finished = 3600

    template {
      metadata {
        labels = { app = "db-init" }
      }
      spec {
        restart_policy = "Never"
        container {
          name  = "psql-runner"
          image = var.pg_image

          env {
            name = "PGPASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.db.metadata[0].name
                key  = "POSTGRES_PASSWORD"
              }
            }
          }

          command = ["/bin/sh", "-ecx"]
          args = [<<-EOT
            until pg_isready -h ${kubernetes_service_v1.rw.metadata[0].name}.${kubernetes_namespace_v1.this.metadata[0].name}.svc.cluster.local -U postgres -q; do sleep 3; done;
            cd /sql;
            for f in /sql/0*.sql; do echo ">>> $$f"; psql -h ${kubernetes_service_v1.rw.metadata[0].name}.${kubernetes_namespace_v1.this.metadata[0].name}.svc.cluster.local -U postgres -d ${var.db_name} -v ON_ERROR_STOP=1 -f "$$f" || exit 1; done;
            echo MIGRACION_COMPLETA
          EOT
          ]

          volume_mount {
            name       = "sql"
            mount_path = "/sql"
            read_only  = true
          }
        }

        volume {
          name = "sql"
          config_map {
            name = kubernetes_config_map_v1.sql_scripts.metadata[0].name
          }
        }
      }
    }
  }

  depends_on = [
    kubernetes_stateful_set_v1.postgres,
    kubernetes_config_map_v1.sql_scripts,
  ]
}
