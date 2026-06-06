"use client";

import { useCallback, useState } from "react";
import { useDropzone } from "react-dropzone";
import { toast } from "sonner";
import { useDefaultProject, useUploadAsset } from "@/hooks/useLibrary";
import { cn } from "@/lib/utils/cn";
import { formatFileSize } from "@/lib/utils/formatters";

type QueueItem = {
  id: string;
  file: File;
  phase: "queued" | "uploading" | "done" | "error";
  fraction: number;
  error?: string;
};

interface Props {
  /** Called after all dropped files finish (any succeeded). */
  onComplete?: () => void;
}

export function VideoUploadDropzone({ onComplete }: Props) {
  const { data: project, isLoading: projectLoading } = useDefaultProject();
  const upload = useUploadAsset(project?.id);
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [busy, setBusy] = useState(false);

  const onDrop = useCallback(
    async (accepted: File[]) => {
      if (!project?.id) {
        toast.error("No project ready yet — try again in a moment");
        return;
      }
      if (accepted.length === 0) return;

      const items: QueueItem[] = accepted.map((file) => ({
        id: `${file.name}-${file.size}-${crypto.randomUUID()}`,
        file,
        phase: "queued",
        fraction: 0,
      }));
      setQueue((q) => [...items, ...q]);
      setBusy(true);

      let succeeded = 0;
      for (const item of items) {
        setQueue((q) =>
          q.map((it) => (it.id === item.id ? { ...it, phase: "uploading" } : it))
        );
        try {
          await upload.mutateAsync(item.file);
          succeeded++;
          setQueue((q) =>
            q.map((it) =>
              it.id === item.id ? { ...it, phase: "done", fraction: 1 } : it
            )
          );
        } catch (err) {
          setQueue((q) =>
            q.map((it) =>
              it.id === item.id
                ? { ...it, phase: "error", error: (err as Error).message }
                : it
            )
          );
        }
      }

      setBusy(false);
      if (succeeded > 0) {
        toast.success(`Uploaded ${succeeded} file${succeeded > 1 ? "s" : ""}`);
        onComplete?.();
      }
    },
    [project?.id, upload, onComplete]
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    disabled: projectLoading || busy,
    accept: {
      "video/*": [],
      "audio/*": [],
      "image/*": [],
    },
  });

  // Reflect live progress from the active mutation into its queue row.
  const activeProgress = upload.progress;

  return (
    <div className="space-y-4">
      <div
        {...getRootProps()}
        className={cn(
          "flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-12 text-center transition",
          isDragActive ? "border-indigo-500 bg-indigo-50" : "border-gray-300 bg-white",
          (projectLoading || busy) && "cursor-not-allowed opacity-60"
        )}
      >
        <input {...getInputProps()} />
        <svg className="mb-3 h-10 w-10 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
            d="M7 16a4 4 0 01-.88-7.9A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
        </svg>
        <p className="text-sm font-medium text-gray-900">
          {isDragActive ? "Drop files here" : "Drag & drop, or click to choose files"}
        </p>
        <p className="mt-1 text-xs text-gray-500">Video, audio, or image files</p>
        {projectLoading && <p className="mt-2 text-xs text-gray-400">Preparing library…</p>}
      </div>

      {queue.length > 0 && (
        <ul className="space-y-2">
          {queue.map((item) => {
            const fraction =
              item.phase === "uploading" && activeProgress?.fileName === item.file.name
                ? activeProgress.fraction
                : item.fraction;
            return (
              <li key={item.id} className="rounded-md border bg-white p-3">
                <div className="flex items-center justify-between text-sm">
                  <span className="truncate font-medium text-gray-900" title={item.file.name}>
                    {item.file.name}
                  </span>
                  <span className="ml-2 shrink-0 text-xs text-gray-500">
                    {formatFileSize(item.file.size)}
                  </span>
                </div>
                <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-gray-100">
                  <div
                    className={cn(
                      "h-full rounded-full transition-all",
                      item.phase === "error" ? "bg-red-500" : "bg-indigo-500"
                    )}
                    style={{
                      width:
                        item.phase === "done"
                          ? "100%"
                          : `${Math.round(fraction * 100)}%`,
                    }}
                  />
                </div>
                <p className="mt-1 text-xs text-gray-500">
                  {item.phase === "queued" && "Queued"}
                  {item.phase === "uploading" && `Uploading… ${Math.round(fraction * 100)}%`}
                  {item.phase === "done" && "Done"}
                  {item.phase === "error" && (
                    <span className="text-red-600">{item.error ?? "Failed"}</span>
                  )}
                </p>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
