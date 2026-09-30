export const MAX_UPLOAD_BYTES = 25 * 1024 * 1024;
export const ALLOWED_EXTENSIONS = ["pdf", "epub", "txt", "md", "markdown"];
export const ACCEPT = ALLOWED_EXTENSIONS.map((e) => `.${e}`).join(",");

/** Client-side pre-check; the server validates again (extension, declared type and magic bytes). */
export function checkUpload(file: File): string | null {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!ALLOWED_EXTENSIONS.includes(ext)) return "Allowed formats: PDF, EPUB, TXT, Markdown.";
  if (file.size === 0) return "That file is empty.";
  if (file.size > MAX_UPLOAD_BYTES) return "That file is larger than 25 MB.";
  return null;
}

export function formatSize(bytes: number) {
  return bytes > 1_048_576
    ? `${(bytes / 1_048_576).toFixed(1)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}
