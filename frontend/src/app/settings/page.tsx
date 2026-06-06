export default function SettingsPage() {
  return (
    <div className="max-w-2xl">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="mt-1 text-sm text-gray-500">Configure your pipeline defaults</p>
      </div>
      <div className="space-y-6">
        <section className="rounded-lg border bg-white p-6">
          <h2 className="font-semibold text-gray-900 mb-4">YouTube</h2>
          <p className="text-sm text-gray-500">
            YouTube OAuth credentials — configured in Phase 7.
          </p>
        </section>
        <section className="rounded-lg border bg-white p-6">
          <h2 className="font-semibold text-gray-900 mb-4">Render Settings</h2>
          <p className="text-sm text-gray-500">
            Default resolution, FPS, and encoding settings — configured in Phase 5.
          </p>
        </section>
      </div>
    </div>
  );
}
