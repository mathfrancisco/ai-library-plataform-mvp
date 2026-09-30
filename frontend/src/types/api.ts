export type Book = {
  id?: string;
  provider?: string;
  externalId?: string;
  isbn13?: string;
  isbn10?: string;
  title: string;
  subtitle?: string;
  authors: string[];
  categories?: string[];
  description?: string;
  language?: string;
  publisher?: string;
  publishedYear?: number;
  pageCount?: number;
  coverUrl?: string;
  publicDomain?: boolean;
};
export type CatalogPage = { items: Book[]; total: number };
export type MatchSource = "LEXICAL" | "SEMANTIC" | "EXTERNAL";
export type SearchHit = {
  localBookId?: string;
  provider?: string;
  externalId?: string;
  title: string;
  authors: string[];
  coverUrl?: string;
  description?: string;
  score: number;
  matchType: MatchSource | "HYBRID";
  matchedBy?: MatchSource[];
};
export type Discovery = {
  plan: { query: string; language?: string; maxPages?: number; categories?: string[] };
  results: SearchHit[];
};
export type LibraryStatus = "WANT_TO_READ" | "READING" | "READ" | "DROPPED";
export const LIBRARY_STATUSES: { value: LibraryStatus; label: string }[] = [
  { value: "WANT_TO_READ", label: "Want to read" },
  { value: "READING", label: "Reading" },
  { value: "READ", label: "Read" },
  { value: "DROPPED", label: "Dropped" },
];
export type LibraryItem = {
  book: Book;
  status: LibraryStatus;
  favorite: boolean;
  rating?: number;
  addedAt: string;
};
export type Progress = {
  bookId: string;
  currentPage: number;
  percentage: number;
  startedAt?: string;
  completedAt?: string;
  notes?: string;
};
export type DocumentItem = {
  id: string;
  bookId?: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  status: "STORED" | "PROCESSING" | "READY" | "FAILED";
  errorMessage?: string;
  chunkCount: number;
  createdAt: string;
};
export type RagAnswer = {
  answer: string;
  sources: { label: string; source: string; chunkIndex: string; snippet: string }[];
};
export type SummaryType = "TLDR" | "SHORT" | "FULL" | "TAKEAWAYS";
export type Recommendation = { book: Book; score: number; reasons: string[] };
export type CurrentlyReading = {
  bookId: string;
  title: string;
  coverUrl?: string;
  currentPage: number;
  pageCount?: number;
  percentage: number;
};
export type Dashboard = {
  totalBooks: number;
  wantToRead: number;
  reading: number;
  read: number;
  dropped: number;
  favorites: number;
  pagesTracked: number;
  averageProgress: number;
  averageRating?: number | null;
  completedThisYear: number;
  currentlyReading: CurrentlyReading[];
};
export type User = { id: string; email: string; displayName: string; role: "USER" | "ADMIN" };
export type AuthResponse = { accessToken: string; refreshToken: string; user: User };
export type AiUsage = {
  requests: number;
  successful: number;
  inputTokens: number;
  outputTokens: number;
  averageLatencyMs: number;
};
export type ApiErrorBody = { code: string; message: string; timestamp: string; path: string };
