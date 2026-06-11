"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { Spinner } from "@/components/ui/Spinner";
import { MonoLabel } from "@/components/ui/MonoLabel";
import {
  useCreatePipelineRun,
  useDefaultProjectForPipeline,
  useTemplates,
} from "@/hooks/usePipelines";
import { useAssets } from "@/hooks/useLibrary";

export default function NewPipelinePage() {
  const router = useRouter();
  const { data: project } = useDefaultProjectForPipeline();
  const { data: templates, isLoading: templatesLoading } = useTemplates();
  const { data: assets } = useAssets(project?.id);
  const createRun = useCreatePipelineRun();

  const [prompt, setPrompt] = useState("");
  const [genre, setGenre] = useState("");
  const [tone, setTone] = useState("");
  const [gameplayAssetId, setGameplayAssetId] = useState("");

  const template = templates?.find((t) => t.name === "STORY_GAMEPLAY_VIDEO_V1");
  const readyAssets = assets?.filter((a) => a.status === "READY" && a.assetType === "GAMEPLAY_VIDEO") ?? [];

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!project || !template) return;
    try {
      const run = await createRun.mutateAsync({
        projectId: project.id,
        body: {
          pipelineTemplateId: template.id,
          inputAssetIds: gameplayAssetId ? { gameplay: gameplayAssetId } : {},
          inputParams: { prompt, genre: genre || undefined, tone: tone || undefined },
        },
      });
      toast.success("Pipeline run started");
      router.push(`/pipelines/${run.id}`);
    } catch (err) {
      toast.error((err as Error).message ?? "Failed to start pipeline");
    }
  }

  if (templatesLoading) {
    return <div className="flex justify-center py-20"><Spinner className="h-5 w-5 text-slate-400" /></div>;
  }

  if (!template) {
    return (
      <div className="max-w-xl rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-700">
        Pipeline template not found. Make sure the backend is running.
      </div>
    );
  }

  const inputBase = "w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:bg-white focus:border-accent focus:outline-none focus:ring-2 focus:ring-blue-100 transition";

  return (
    <div className="max-w-xl">
      {/* Dark page header */}
      <div className="relative rounded-xl overflow-hidden bg-ink-950 grid-overlay mb-7 px-7 py-8">
        <span className="absolute top-3 left-3 w-2 h-2 border-t border-l border-white/20" />
        <span className="absolute bottom-3 right-3 w-2 h-2 border-b border-r border-white/20" />
        <MonoLabel className="text-ink-400 block mb-3">{template.steps.length} steps · {template.name}</MonoLabel>
        <h1 className="text-2xl font-bold text-white tracking-tight">New Pipeline Run</h1>
        <p className="mt-1 text-sm text-ink-300">Generate a short-form story gameplay video</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* Prompt */}
        <div className="rounded-xl border border-slate-200 bg-white shadow-sm p-5">
          <label htmlFor="prompt" className="block text-sm font-semibold text-slate-800 mb-1.5">
            Story Prompt <span className="text-red-400">*</span>
          </label>
          <textarea
            id="prompt" rows={4} required
            value={prompt} onChange={(e) => setPrompt(e.target.value)}
            placeholder="A ninja discovers a magical sword in an ancient temple…"
            className={`${inputBase} resize-none`}
          />
        </div>

        {/* Style */}
        <div className="rounded-xl border border-slate-200 bg-white shadow-sm p-5">
          <p className="text-sm font-semibold text-slate-800 mb-3">
            Style <span className="text-slate-400 font-normal text-xs">(optional)</span>
          </p>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="genre" className="block text-xs font-medium text-slate-500 mb-1">Genre</label>
              <input id="genre" type="text" value={genre} onChange={(e) => setGenre(e.target.value)}
                placeholder="Action, Drama…" className={inputBase} />
            </div>
            <div>
              <label htmlFor="tone" className="block text-xs font-medium text-slate-500 mb-1">Tone</label>
              <input id="tone" type="text" value={tone} onChange={(e) => setTone(e.target.value)}
                placeholder="Epic, Funny…" className={inputBase} />
            </div>
          </div>
        </div>

        {/* Gameplay clip */}
        {readyAssets.length > 0 && (
          <div className="rounded-xl border border-slate-200 bg-white shadow-sm p-5">
            <label htmlFor="gameplay" className="block text-sm font-semibold text-slate-800 mb-1.5">
              Gameplay Clip <span className="text-slate-400 font-normal text-xs">(optional)</span>
            </label>
            <select id="gameplay" value={gameplayAssetId} onChange={(e) => setGameplayAssetId(e.target.value)}
              className={inputBase}>
              <option value="">— None —</option>
              {readyAssets.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </div>
        )}

        {/* Submit row */}
        <div className="flex items-center justify-end pt-1">
          <button
            type="submit"
            disabled={createRun.isPending || !prompt.trim()}
            className="inline-flex items-center gap-2 rounded-lg bg-accent hover:bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {createRun.isPending ? <><Spinner className="h-4 w-4" />Starting…</> : <>Start pipeline <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 7h8M7 3l4 4-4 4" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg></>}
          </button>
        </div>
      </form>
    </div>
  );
}
