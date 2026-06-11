import { cn } from "@/lib/utils/cn";

/** Uppercase monospace metadata label — the Mistral-style annotation text. */
export function MonoLabel({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={cn("font-mono text-xs uppercase tracking-widest", className)}>
      {children}
    </span>
  );
}
