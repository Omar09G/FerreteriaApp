output "namespace" {
  value = kubernetes_namespace_v1.this.metadata[0].name
}

output "postgres_service" {
  value = "postgres-rw.${kubernetes_namespace_v1.this.metadata[0].name}.svc.cluster.local:5432"
}

output "pgbouncer_service" {
  value = "pgbouncer.${kubernetes_namespace_v1.this.metadata[0].name}.svc.cluster.local:6432"
}

# Cadena lista para el backend Java (Spring: spring.datasource.url)
output "jdbc_url_via_pgbouncer" {
  value = "jdbc:postgresql://${kubernetes_service_v1.pgbouncer.metadata[0].name}.${kubernetes_namespace_v1.this.metadata[0].name}.svc.cluster.local:6432/${var.db_name}"
}

output "app_user" {
  value = var.app_user
}
