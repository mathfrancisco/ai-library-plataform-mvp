export function Skeleton({ height = 16, width = "100%" }: { height?: number; width?: number | string }) {
  return <div className="skeleton" style={{ height, width }} aria-hidden="true" />;
}

export function CardGridSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="grid" aria-busy="true" aria-label="Loading">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="bookcard">
          <Skeleton height={260} />
          <div className="bookmeta">
            <Skeleton width="60%" />
            <Skeleton />
          </div>
        </div>
      ))}
    </div>
  );
}
