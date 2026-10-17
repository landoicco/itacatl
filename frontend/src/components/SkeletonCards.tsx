interface SkeletonCardsProps {
  count?: number;
  /** Tailwind height class of each placeholder */
  height?: string;
}

/** Placeholders shaped like the cards that are loading, so the layout does not jump */
export default function SkeletonCards({ count = 3, height = "h-40" }: SkeletonCardsProps) {
  return (
    <div aria-busy="true" className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-5">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className={`${height} animate-pulse rounded-card bg-soft`} />
      ))}
    </div>
  );
}
