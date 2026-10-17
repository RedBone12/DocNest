import { useId, useState } from "react";
import { Eye, EyeOff } from "lucide-react";

export function PasswordField({ label, value, onChange, autoComplete = "new-password", minLength }: {
  label: string; value: string; onChange: (value: string) => void;
  autoComplete?: string; minLength?: number;
}) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  return <div>
    <label htmlFor={id} className="mb-1 block text-sm font-medium">{label}</label>
    <div className="relative">
      <input id={id} type={visible ? "text" : "password"} autoComplete={autoComplete}
        required minLength={minLength} maxLength={72} value={value} onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-lg border bg-gray-50 py-2 pl-3 pr-12" />
      <button type="button" aria-label={`${visible ? "Hide" : "Show"} ${label.toLowerCase()}`} aria-pressed={visible}
        onClick={() => setVisible(!visible)} className="absolute right-1 top-0 rounded-lg p-2.5 text-gray-500">
        {visible ? <EyeOff size={18} /> : <Eye size={18} />}
      </button>
    </div>
  </div>;
}
