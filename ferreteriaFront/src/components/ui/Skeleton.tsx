type Props = {
  className?: string;
  lines?: number;
};

/**
 * Skeleton para reportes: rectángulos grises que imitan gráfico + tabla
 * mientras la query está en isLoading. Evita spinner global genérico.
 */
export function Skeleton({ className = "", lines = 3 }: Props) {
  return (
    <div className={`animate-pulse motion-reduce:animate-none space-y-3 ${className}`} aria-busy="true" aria-live="polite">
      <div className="h-48 rounded bg-line" />
      {Array.from({ length: lines }).map((_, i) => (
        <div key={i} className="h-4 rounded bg-line" style={{ width: `${100 - i * 10}%` }} />
      ))}
    </div>
  );
}

export function ChartSkeleton() {
  return (
    <div className="animate-pulse motion-reduce:animate-none" aria-busy="true" aria-live="polite">
      <div className="h-64 rounded bg-line" />
    </div>
  );
}

export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="animate-pulse motion-reduce:animate-none space-y-2" role="status" aria-busy="true" aria-live="polite">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="h-8 rounded bg-line" />
      ))}
    </div>
  );
}
