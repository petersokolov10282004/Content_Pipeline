import Link from "next/link";

export default function DashboardPage() {
  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="mt-1 text-sm text-gray-500">Welcome to ContentPipeline</p>
      </div>
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <QuickActionCard
          title="New Pipeline"
          description="Generate a short-form story gameplay video"
          href="/pipelines/new"
          label="Get Started"
        />
        <QuickActionCard
          title="Gameplay Library"
          description="Upload and manage your gameplay video clips"
          href="/library"
          label="Open Library"
        />
        <QuickActionCard
          title="Recent Runs"
          description="View and track your pipeline executions"
          href="/pipelines"
          label="View All"
        />
      </div>
    </div>
  );
}

function QuickActionCard({
  title,
  description,
  href,
  label,
}: {
  title: string;
  description: string;
  href: string;
  label: string;
}) {
  return (
    <div className="rounded-lg border bg-white p-6 shadow-sm">
      <h3 className="font-semibold text-gray-900">{title}</h3>
      <p className="mt-1 text-sm text-gray-500">{description}</p>
      <Link
        href={href}
        className="mt-4 inline-flex items-center text-sm font-medium text-indigo-600 hover:text-indigo-500"
      >
        {label} →
      </Link>
    </div>
  );
}
