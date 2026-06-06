export function DevModeBanner() {
  if (process.env.NEXT_PUBLIC_AUTH_ENABLED === "true") return null;
  return (
    <div className="w-full bg-amber-500 text-amber-950 text-xs font-semibold text-center py-1 px-4 z-50">
      DEV MODE — Authentication disabled. Not for production use.
    </div>
  );
}
