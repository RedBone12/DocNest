type HighlightedTextProps = {
  text: string;
};

export function HighlightedText({ text }: HighlightedTextProps) {
  const parts = text.split(
    /(\[\[HIGHLIGHT\]\][\s\S]*?\[\[\/HIGHLIGHT\]\])/g
  );

  const startTag = "[[HIGHLIGHT]]";
  const endTag = "[[/HIGHLIGHT]]";

  return (
    <>
      {parts.map((part, index) => {
        const highlighted =
          part.startsWith(startTag) && part.endsWith(endTag);

        if (!highlighted) {
          return <span key={index}>{part}</span>;
        }

        const word = part.slice(
          startTag.length,
          -endTag.length
        );

        return (
          <mark
            key={index}
            className="rounded-sm bg-yellow-200 px-0.5 text-gray-900"
          >
            {word}
          </mark>
        );
      })}
    </>
  );
}