/**
 * Dark ink canvas with subtle grid lines and corner tick marks.
 * Used on the sidebar header and dark page sections.
 */
export function GridBackdrop({ children, className = "" }: { children?: React.ReactNode; className?: string }) {
  return (
    <div className={`relative overflow-hidden bg-ink-950 grid-overlay tick tick-tl tick-br ${className}`}>
      {children}
    </div>
  );
}
