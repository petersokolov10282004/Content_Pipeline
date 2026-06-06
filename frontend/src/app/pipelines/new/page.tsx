"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { Spinner } from "@/components/ui/Spinner";
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
          inputParams: {
            prompt,
            genre: genre || undefined,
            tone: tone || undefined,
          },
        },
      });
      toast.success("Pipeline run started");
      router.push(`/pipelines/${run.id}`);
    } catch (err) {
      toast.error((err as Error).message ?? "Failed to start pipeline");
    }
  }

  if (templatesLoading) {
    return (
      <div className="flex justify-center py-16 text-gray-400">
        <Spinner className="h-6 w-6" />
      </div>
    );
  }

  if (!template) {
    return (
      <div className="max-w-2xl rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Pipeline template not found. Make sure the backend is running and has seeded the template.
      </div>
    );
  }

  return (
    <div className="max-w-2xl">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">New Pipeline Run</h1>
        <p className="mt-1 text-sm text-gray-500">Generate a short-form story gameplay video</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border bg-white p-6">
        <div>
          <label htmlFor="prompt" className="block text-sm font-medium text-gray-700">
            Story Prompt <span className="text-red-500">*</span>
          </label>
          <textarea
            id="prompt"
            rows={4}
            required
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="A ninja discovers a magical sword in an ancient temple…"
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="genre" className="block text-sm font-medium text-gray-700">Genre</label>
            <input
              id="genre"
              type="text"
              value={genre}
              onChange={(e) => setGenre(e.target.value)}
              placeholder="Action, Comedy, Drama…"
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label htmlFor="tone" className="block text-sm font-medium text-gray-700">Tone</label>
            <input
              id="tone"
              type="text"
              value={tone}
              onChange={(e) => setTone(e.target.value)}
              placeholder="Epic, Funny, Mysterious…"
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>
        </div>

        {readyAssets.length > 0 && (
          <div>
            <label htmlFor="gameplay" className="block text-sm font-medium text-gray-700">
              Gameplay Video (optional)
            </label>
            <select
              id="gameplay"
              value={gameplayAssetId}
              onChange={(e) => setGameplayAssetId(e.target.value)}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            >
              <option value="">— None —</option>
              {readyAssets.map((a) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
          </div>
        )}

        <div className="flex items-center justify-between pt-2">
          <p className="text-xs text-gray-400">
            Template: {template.name} · {template.steps.length} steps
          </p>
          <button
            type="submit"
            disabled={createRun.isPending || !prompt.trim()}
            className="flex items-center gap-2 rounded-md bg-indigo-600 px-5 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 disabled:opacity-60"
          >
            {createRun.isPending && <Spinner className="h-4 w-4" />}
            Start Pipeline
          </button>
        </div>
      </form>
    </div>
  );
}
