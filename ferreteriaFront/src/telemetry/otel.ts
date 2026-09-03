/**
 * OpenTelemetry browser SDK — Ferretería "El Tornillo Feliz"
 * ----------------------------------------------------------------------------
 * Inicializa trazas + métricas en el browser y las envía al OpenTelemetry
 * Collector (puerto 4318 OTLP HTTP, CORS ya configurado).
 *
 * Señales emitidas:
 *  - Traces: navegación (page load), fetch (API calls), user interactions
 *  - Metrics: Web Vitals (LCP, FID, CLS, FCP, TTFB), errores JS, duración
 *    de navegación
 *
 * Activación:
 *  - VITE_OTEL_ENABLED=true       → emite telemetría
 *  - VITE_OTEL_ENABLED=false      → noop (dev local, ahorro de CPU/red)
 *  - VITE_OTEL_EXPORTER_OTLP_ENDPOINT
 *    → URL del collector. Default: http://localhost:4318 (vía nginx)
 *
 * NOTA: se inicializa ANTES de React/router para que la primera navegación
 * quede capturada. Las auto-instrumentaciones se enganchan al SDK apenas se
 * registran.
 */
import { metrics, trace } from "@opentelemetry/api";
import { OTLPMetricExporter } from "@opentelemetry/exporter-metrics-otlp-http";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { DocumentLoadInstrumentation } from "@opentelemetry/instrumentation-document-load";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { UserInteractionInstrumentation } from "@opentelemetry/instrumentation-user-interaction";
import { registerInstrumentations } from "@opentelemetry/instrumentation";
import { resourceFromAttributes } from "@opentelemetry/resources";
import {
	MeterProvider,
	PeriodicExportingMetricReader,
} from "@opentelemetry/sdk-metrics";
import {
	BatchSpanProcessor,
	WebTracerProvider,
} from "@opentelemetry/sdk-trace-web";
import {
	ATTR_SERVICE_NAME,
	ATTR_SERVICE_VERSION,
} from "@opentelemetry/semantic-conventions";
import { onCLS, onFCP, onINP, onLCP, onTTFB } from "web-vitals";

const enabled = import.meta.env.VITE_OTEL_ENABLED === "true";
const endpoint =
	import.meta.env.VITE_OTEL_EXPORTER_OTLP_ENDPOINT ||
	`${window.location.protocol}//${window.location.hostname}:4318`;

if (!enabled) {
	// Modo noop: el tracer/meter global queda sin exporter, los spans son
	// no-op (cero costo en producción local / dev).
	console.info('[OTel] Desactivado (VITE_OTEL_ENABLED !== "true")');
} else {
	// ─── Recurso común: identifica al servicio en el collector ─────────
	const resource = resourceFromAttributes({
		[ATTR_SERVICE_NAME]: "ferreteria-frontend",
		[ATTR_SERVICE_VERSION]: import.meta.env.VITE_APP_VERSION || "0.0.0",
		"deployment.environment": "docker",
		"browser.user_agent": navigator.userAgent,
	});

	// ─── Traces ────────────────────────────────────────────────────────
	const traceExporter = new OTLPTraceExporter({
		url: `${endpoint}/v1/traces`,
	});
	const tracerProvider = new WebTracerProvider({
		resource,
		spanProcessors: [
			new BatchSpanProcessor(traceExporter, {
				maxQueueSize: 100,
				maxExportBatchSize: 50,
				scheduledDelayMillis: 5_000,
			}),
		],
	});
	tracerProvider.register();

	// Auto-instrumentaciones: navegación, fetch, interacciones. Se registran
	// DESPUÉS de tracerProvider.register() para que tomen el provider global.
	registerInstrumentations({
		instrumentations: [
			new DocumentLoadInstrumentation(),
			new FetchInstrumentation({
				// No rastrear llamadas a /csrf-init (ruido) ni a collectores OTel
				ignoreUrls: [/\/auth\/csrf-init/, /\/v1\/(traces|metrics)$/],
				// Añade timing attributes en cada fetch
				measureRequestSize: true,
			}),
			new UserInteractionInstrumentation({
				// No spamear clicks de elementos del sistema (botones cerrar, etc.)
				eventNames: ["click", "submit"],
			}),
		],
	});

	// ─── Metrics ───────────────────────────────────────────────────────
	const meterProvider = new MeterProvider({
		resource,
		readers: [
			new PeriodicExportingMetricReader({
				exporter: new OTLPMetricExporter({
					url: `${endpoint}/v1/metrics`,
				}),
				// Export cada 30s (alineado con scrape_interval de Prometheus).
				exportIntervalMillis: 30_000,
			}),
		],
	});
	metrics.setGlobalMeterProvider(meterProvider);

	// ─── Web Vitals → métricas custom ─────────────────────────────────
	// web-vitals entrega deltas; los contamos y exponemos como gauges/histograms.
	const meter = metrics.getMeter("ferreteria-frontend", "1.0.0");
	const lcpHistogram = meter.createHistogram("frontend.web_vitals.lcp", {
		description: "Largest Contentful Paint (s)",
		unit: "s",
	});
	const clsHistogram = meter.createHistogram("frontend.web_vitals.cls", {
		description: "Cumulative Layout Shift (score)",
	});
	const fcpHistogram = meter.createHistogram("frontend.web_vitals.fcp", {
		description: "First Contentful Paint (s)",
		unit: "s",
	});
	const ttfbHistogram = meter.createHistogram("frontend.web_vitals.ttfb", {
		description: "Time to First Byte (s)",
		unit: "s",
	});
	const inpcounter = meter.createHistogram("frontend.web_vitals.inp", {
		description: "Interaction to Next Paint (s)",
		unit: "s",
	});

	onLCP((m: { value: number }) => lcpHistogram.record(m.value));
	onCLS((m: { value: number }) => clsHistogram.record(m.value));
	onFCP((m: { value: number }) => fcpHistogram.record(m.value));
	onTTFB((m: { value: number }) => ttfbHistogram.record(m.value));
	onINP((m: { value: number }) => inpcounter.record(m.value));

	// ─── Errores JS → counter ─────────────────────────────────────────
	const errorCounter = meter.createCounter("frontend.js.errors", {
		description: "Unhandled JS errors (window.onerror)",
	});
	window.addEventListener("error", (e) => {
		errorCounter.add(1, {
			"error.type": e.error?.name || "Error",
			"error.message": e.message.slice(0, 200),
		});
	});
	window.addEventListener("unhandledrejection", (e) => {
		errorCounter.add(1, {
			"error.type": "UnhandledRejection",
			"error.message": String(e.reason).slice(0, 200),
		});
	});

	// ─── Navegación custom ────────────────────────────────────────────
	// La DocumentLoadInstrumentation emite el span; aquí exponemos duración
	// como métrica también para que Grafana la grafique con percentiles.
	const navHistogram = meter.createHistogram("frontend.navigation.duration", {
		description: "Duración total de carga de página (s)",
		unit: "s",
	});
	window.addEventListener("load", () => {
		const t = performance.getEntriesByType("navigation")[0] as
			| PerformanceNavigationTiming
			| undefined;
		if (t) navHistogram.record(t.duration / 1000);
	});

	console.info(`[OTel] Activo → ${endpoint}`);
}

// Helper para que otros módulos puedan agregar atributos al span activo
export function getTracer() {
	return trace.getTracer("ferreteria-frontend");
}
