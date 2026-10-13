import { Menu, User } from "lucide-react";

type Props = {
  onMenuClick: () => void;
  onUserClick: () => void;
};

export const Header = ({ onMenuClick, onUserClick }: Props) => {
  return (
    <header className="h-16 bg-white border-b flex items-center px-6 justify-between">
      
      {/* Left */}
      <div className="flex items-center gap-4">
        <button
          aria-label="Open navigation menu"
          onClick={onMenuClick}
          className="p-2 hover:bg-gray-100 rounded-md"
        >
          <Menu size={20} />
        </button>

        <div className="flex items-center gap-2">
          <div className="bg-black text-white p-2 rounded-lg">
            📄
          </div>
          <span className="font-semibold text-lg">
            Document Manager
          </span>
        </div>
      </div>

      {/* Right */}
      <button aria-label="Open user profile" onClick={onUserClick} className="p-2 hover:bg-gray-100 rounded-full">
        <User size={20} />
      </button>
    </header>
  );
};
