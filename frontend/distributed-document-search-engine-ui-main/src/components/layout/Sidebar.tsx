import { FileText, Upload, Search, Settings, X } from "lucide-react";
import { useRef } from "react";
import { useDialogFocus } from "../../hooks/useDialogFocus";

type Props = {
  open: boolean;
  onClose: () => void;
  onUpload: () => void;
  onSearch: () => void;
  onDocuments:() => void;
  onSettings: () => void;
};

const navItems = [
  { label: "Documents", icon: FileText },
  { label: "Upload", icon: Upload },
  { label: "Search", icon: Search },
  { label: "Settings", icon: Settings },
];

export const Sidebar = ({ open, onClose, onUpload, onSearch, onDocuments, onSettings}: Props) => {
  const dialogRef = useRef<HTMLElement>(null);
  useDialogFocus(open, dialogRef, onClose);
  const handleMenuClick = (label: string) => {
    if (label === "Upload") {
      onUpload();
    } else if (label === "Search") {
      onSearch();
    }else if (label === "Documents") {
      onDocuments();
    }else if(label === "Settings") {
      onSettings();
    }
  };
  return (
    <>
      {open && (
        <div className="fixed inset-0 bg-black/30 z-40" onClick={onClose} />
      )}

      <aside
        ref={dialogRef}
        inert={!open}
        role="dialog"
        aria-modal={open || undefined}
        aria-label="Navigation menu"
        tabIndex={-1}
        className={`
          fixed z-50
          inset-y-0 left-0 h-full w-64 max-w-[90vw] bg-white border-r
          transform transition-transform duration-300
          ${open ? "translate-x-0" : "-translate-x-full"}
        `}
      >
        <div className="flex items-center justify-between p-4">
          <span className="font-semibold">Menu</span>
          <button onClick={onClose} aria-label="Close menu">
            <X size={20} />
          </button>
        </div>

        <nav className="p-4 space-y-2">
          {navItems.map(({ label, icon: Icon }) => (
            <button
              key={label}
              onClick={() => handleMenuClick(label)}
              className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-gray-700 hover:bg-gray-100"
            >
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
      </aside>
    </>
  );
};
