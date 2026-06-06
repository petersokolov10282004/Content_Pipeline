"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { VideoUploadDropzone } from "@/components/library/VideoUploadDropzone";

export default function LibraryUploadPage() {
  const router = useRouter();

  return (
    <div className="max-w-2xl">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Upload Gameplay Video</h1>
          <p className="mt-1 text-sm text-gray-500">Add new clips to your library</p>
        </div>
        <Link href="/library" className="text-sm font-medium text-indigo-600 hover:text-indigo-500">
          ← Back to library
        </Link>
      </div>

      <VideoUploadDropzone onComplete={() => router.refresh()} />

      <div className="mt-6 text-right">
        <Link
          href="/library"
          className="rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
        >
          Done
        </Link>
      </div>
    </div>
  );
}
