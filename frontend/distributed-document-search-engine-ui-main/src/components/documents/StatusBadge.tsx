import type { Status } from "../common/document";


const statusStyles: Record<Status, string> = {
  READY: "bg-green-100 text-green-700",
  UPLOADED: "bg-blue-100 text-blue-700",
  INDEXING: "bg-yellow-100 text-yellow-700",
  FAILED: "bg-red-100 text-red-700",
};

type Props = {
  status: Status;
};

export const StatusBadge = ({ status }: Props) => {
  return (
    <span
      className={`px-3 py-1 rounded-full text-xs font-medium ${statusStyles[status]}`}
    >
      {status}
    </span>
  );
};
