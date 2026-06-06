export default function PipelineRunPage({ params }: { params: { runId: string } }) {
  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Pipeline Run</h1>
      <p className="text-sm text-gray-500">Run ID: {params.runId}</p>
      {/* PipelineRunStatusBoard + StepProgressTracker — implemented in Phase 4 */}
    </div>
  );
}
