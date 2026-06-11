import Link from "next/link";
import { MonoLabel } from "@/components/ui/MonoLabel";

// ── Pixel block motif (echoes the Devstral checker pattern) ──────────────────
function PixelMotif() {
  const lit = [
    [0,1],[0,3],[1,0],[1,2],[1,4],[2,1],[2,3],[3,0],[3,2],[3,4],[4,1],[4,3],
  ];
  return (
    <div className="grid gap-[3px]" style={{ gridTemplateColumns: "repeat(5,10px)" }}>
      {Array.from({ length: 25 }, (_, i) => {
        const r = Math.floor(i / 5), c = i % 5;
        const on = lit.some(([lr, lc]) => lr === r && lc === c);
        return <div key={i} className={`w-[10px] h-[10px] rounded-[1px] ${on ? "bg-white/25" : "bg-white/5"}`} />;
      })}
    </div>
  );
}

// ── Vertical accent stripe ────────────────────────────────────────────────────
function AccentStripe() {
  return <div className="w-1 self-stretch bg-accent rounded-full opacity-80" />;
}

export default function DashboardPage() {
  return (
    <div>
      {/* ── Dark hero ─────────────────────────────────────────────────────── */}
      <div className="relative rounded-xl overflow-hidden bg-ink-950 grid-overlay mb-8 px-8 py-10">
        {/* corner ticks */}
        <span className="absolute top-4 left-4 w-3 h-3 border-t border-l border-white/20" />
        <span className="absolute bottom-4 right-4 w-3 h-3 border-b border-r border-white/20" />

        {/* pixel motif top-right */}
        <div className="absolute top-8 right-8 opacity-60">
          <PixelMotif />
        </div>

        <MonoLabel className="text-ink-300 mb-4 block">ContentPipeline · v1</MonoLabel>

        <h1 className="text-4xl font-bold text-white tracking-tight leading-none mb-3">
          Dashboard
        </h1>
        <p className="text-ink-300 text-sm max-w-sm">
          Automated short-form content generation. Prompt in, video out.
        </p>

        <div className="flex items-center gap-3 mt-6">
          <Link
            href="/pipelines/new"
            className="inline-flex items-center gap-2 bg-accent hover:bg-blue-600 text-white text-sm font-semibold px-4 py-2 rounded-md transition-colors"
          >
            New pipeline run
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M3 7h8M7 3l4 4-4 4" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </Link>
          <Link href="/pipelines" className="text-sm text-ink-300 hover:text-white transition-colors">
            View runs →
          </Link>
        </div>
      </div>

      {/* ── Quick action cards ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <ActionCard
          tag="Pipeline"
          title="New Run"
          description="Generate a short-form story gameplay video from a text prompt."
          href="/pipelines/new"
          cta="Start now"
        />
        <ActionCard
          tag="Library"
          title="Gameplay Clips"
          description="Upload and manage gameplay video clips used as render backgrounds."
          href="/library"
          cta="Open library"
        />
        <ActionCard
          tag="History"
          title="Recent Runs"
          description="View status, step progress, and output for all pipeline runs."
          href="/pipelines"
          cta="View all"
        />
      </div>
    </div>
  );
}

function ActionCard({ tag, title, description, href, cta }: {
  tag: string; title: string; description: string; href: string; cta: string;
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm flex gap-3">
      <div className="w-px self-stretch bg-accent rounded-full opacity-60 shrink-0" />
      <div className="flex flex-col gap-2 flex-1">
        <MonoLabel className="text-slate-400">{tag}</MonoLabel>
        <h3 className="font-semibold text-slate-900 text-sm">{title}</h3>
        <p className="text-xs text-slate-500 leading-relaxed flex-1">{description}</p>
        <Link href={href} className="text-xs font-semibold text-accent hover:text-blue-700 transition-colors mt-1">
          {cta} →
        </Link>
      </div>
    </div>
  );
}
