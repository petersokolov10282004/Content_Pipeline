export default function ArtifactDetailPage({
  params,
}: {
  params: { runId: string; artifactId: string };
}) {
  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-4">Artifact</h1>
      <p className="text-sm text-gray-500">Artifact ID: {params.artifactId}</p>
      {/* ArtifactViewer — implemented in Phase 4 */}
    </div>
  );
}
