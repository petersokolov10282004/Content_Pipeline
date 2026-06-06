"use client";

import { type ReactNode } from "react";
import { Sidebar } from "./Sidebar";
import { DevModeBanner } from "./DevModeBanner";

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <DevModeBanner />
      <div className="flex flex-1 overflow-hidden">
        <Sidebar />
        <main className="flex-1 overflow-y-auto p-8">{children}</main>
      </div>
    </div>
  );
}
