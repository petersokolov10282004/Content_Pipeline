"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils/cn";
import { MonoLabel } from "@/components/ui/MonoLabel";

const navItems = [
  { href: "/",          label: "Dashboard", key: "dashboard" },
  { href: "/pipelines", label: "Pipelines", key: "pipelines" },
  { href: "/library",   label: "Library",   key: "library"   },
  { href: "/settings",  label: "Settings",  key: "settings"  },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-56 shrink-0 flex flex-col bg-ink-950 h-screen sticky top-0 border-r border-white/[0.05]">

      {/* Brand */}
      <div className="relative px-5 pt-7 pb-6 border-b border-white/[0.06] grid-overlay overflow-hidden">
        {/* corner ticks */}
        <span className="absolute top-3 left-3 w-2 h-2 border-t border-l border-white/20" />
        <span className="absolute bottom-3 right-3 w-2 h-2 border-b border-r border-white/20" />

        <MonoLabel className="text-ink-300 mb-2 block">System</MonoLabel>
        <div className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-sm bg-accent flex items-center justify-center shrink-0">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <path d="M2 6.5h5M7 2l4 4.5L7 11" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <span className="text-sm font-semibold text-white tracking-tight">ContentPipeline</span>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-5 space-y-0.5">
        <MonoLabel className="text-ink-400 px-3 mb-3 block">Navigation</MonoLabel>
        {navItems.map((item) => {
          const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "group flex items-center justify-between rounded px-3 py-2 text-sm transition-colors",
                active
                  ? "bg-white/[0.07] text-white"
                  : "text-ink-300 hover:bg-white/[0.04] hover:text-ink-100"
              )}
            >
              <span className="font-medium">{item.label}</span>
              {active && <span className="w-1 h-1 rounded-full bg-accent shrink-0" />}
            </Link>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="px-4 py-4 border-t border-white/[0.06]">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
            <MonoLabel className="text-ink-400">Dev mode</MonoLabel>
          </div>
          <MonoLabel className="text-ink-600">v1</MonoLabel>
        </div>
      </div>
    </aside>
  );
}
