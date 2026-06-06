"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils/cn";

const navItems = [
  { href: "/", label: "Dashboard" },
  { href: "/pipelines", label: "Pipelines" },
  { href: "/library", label: "Library" },
  { href: "/settings", label: "Settings" },
];

export function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="w-56 shrink-0 border-r bg-white flex flex-col py-6 px-3 gap-1">
      <div className="px-3 mb-6">
        <span className="text-sm font-bold tracking-wide text-gray-900">ContentPipeline</span>
      </div>
      {navItems.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className={cn(
            "rounded-md px-3 py-2 text-sm font-medium transition-colors",
            pathname === item.href || (item.href !== "/" && pathname.startsWith(item.href))
              ? "bg-gray-100 text-gray-900"
              : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
          )}
        >
          {item.label}
        </Link>
      ))}
    </aside>
  );
}
