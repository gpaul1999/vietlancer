export default function TopicBadge({
  name,
  icon,
  confidence,
}: {
  name: string;
  icon?: string;
  confidence?: number;
}) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700">
      {icon && <span>{icon}</span>}
      {name}
      {confidence !== undefined && (
        <span className="ml-1 rounded-full bg-brand-100 px-1.5 py-0.5 text-[10px] font-semibold">
          {Math.round(confidence * 100)}%
        </span>
      )}
    </span>
  );
}
