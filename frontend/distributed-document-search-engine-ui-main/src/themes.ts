export const THEMES = [
  { id: "graphite", name: "Graphite", description: "Clean and understated", colors: ["#1f2937", "#f8fafc", "#e2e8f0"] },
  { id: "ocean", name: "Ocean", description: "Cool blue and soft sky", colors: ["#1d4ed8", "#eff6ff", "#bfdbfe"] },
  { id: "forest", name: "Forest", description: "Deep green and fresh mint", colors: ["#047857", "#f0fdf4", "#bbf7d0"] },
  { id: "plum", name: "Plum", description: "Rich purple and gentle lavender", colors: ["#7e22ce", "#faf5ff", "#e9d5ff"] },
  { id: "sunset", name: "Sunset", description: "Warm terracotta and peach", colors: ["#9a3412", "#fff7ed", "#fed7aa"] },
] as const;

export type ThemeName = typeof THEMES[number]["id"];

export function isThemeName(value: unknown): value is ThemeName {
  return THEMES.some((theme) => theme.id === value);
}
