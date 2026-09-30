/** API types mirror the backend DTOs. Keep in sync with docs/04-api-contract.md. */

export type ErrorCode =
  | "BAD_REQUEST"
  | "VALIDATION_ERROR"
  | "UNSUPPORTED_FILE_TYPE"
  | "EMPTY_FILE"
  | "TOO_LARGE_AFTER_EXTRACTION"
  | "DOCUMENT_NOT_READY"
  | "NO_BOOK_DOCUMENTS"
  | "NO_SUMMARY_SOURCE"
  | "PROVIDER_UNAVAILABLE"
  | "WRONG_PASSWORD"
  | "QUOTA_EXCEEDED"
  | "UNAUTHORIZED"
  | "INVALID_CREDENTIALS"
  | "INVALID_REFRESH_TOKEN"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "BOOK_NOT_FOUND"
  | "DOCUMENT_NOT_FOUND"
  | "LIBRARY_ITEM_NOT_FOUND"
  | "EXTERNAL_BOOK_NOT_FOUND"
  | "USER_NOT_FOUND"
  | "CONFLICT"
  | "EMAIL_TAKEN"
  | "FILE_TOO_LARGE"
  | "RATE_LIMITED"
  | "AI_RATE_LIMITED"
  | "INTERNAL_ERROR"
  | "AI_PROVIDER_ERROR"
  | "AI_DISABLED"
  | "VECTOR_DISABLED"
  | "AI_TIMEOUT";

export type ApiErrorBody = {
  code: ErrorCode;
  message: string;
  timestamp: string;
  path: string;
  requestId?: string;
};

/** A book persisted locally: always has an id. */
export type Book = {
  id: string;
  isbn13?: string | null;
  isbn10?: string | null;
  title: string;
  subtitle?: string | null;
  authors: string[];
  categories?: string[];
  description?: string | null;
  language?: string | null;
  publisher?: string | null;
  publishedYear?: number | null;
  pageCount?: number | null;
  coverUrl?: string | null;
  publicDomain?: boolean;
};

/** Compact local book used in lists (no description). */
export type BookSummary = Pick<
  Book,
  "id" | "title" | "subtitle" | "authors" | "categories" | "coverUrl" | "publishedYear" | "pageCount"
>;

/** A record from an external catalog: identified by provider + externalId. */
export type CatalogBook = {
  provider: string;
  externalId: string;
  title: string;
  authors: string[];
  coverUrl?: string | null;
};

export type ProviderStatus = { name: string; ok: boolean };

export type MatchSource = "LEXICAL" | "SEMANTIC" | "EXTERNAL";
export type SearchMode = "HYBRID" | "LEXICAL" | "SEMANTIC";
export type SearchHit = {
  localBookId?: string | null;
  provider?: string | null;
  externalId?: string | null;
  title: string;
  authors: string[];
  coverUrl?: string | null;
  description?: string | null;
  score: number;
  matchType: MatchSource | "HYBRID";
  matchedBy?: MatchSource[];
};
export type SearchResponse = { results: SearchHit[]; degraded: string[]; providers: ProviderStatus[] };
export type DiscoveryPlan = {
  query: string;
  language?: string | null;
  maxPages?: number | null;
  categories?: string[] | null;
};
export type Discovery = SearchResponse & { plan: DiscoveryPlan };

export type LibraryStatus = "WANT_TO_READ" | "READING" | "READ" | "DROPPED";
export const LIBRARY_STATUSES: { value: LibraryStatus; label: string }[] = [
  { value: "WANT_TO_READ", label: "Want to read" },
  { value: "READING", label: "Reading" },
  { value: "READ", label: "Read" },
  { value: "DROPPED", label: "Dropped" },
];

export type Progress = {
  bookId: string;
  exists: boolean;
  currentPage: number;
  percentage: number;
  startedAt?: string | null;
  completedAt?: string | null;
  notes?: string | null;
};

export type LibraryItemSummary = {
  book: BookSummary;
  status: LibraryStatus;
  favorite: boolean;
  rating?: number | null;
  addedAt: string;
  updatedAt: string;
  percentage: number;
};

export type LibraryItem = {
  book: Book;
  status: LibraryStatus;
  favorite: boolean;
  rating?: number | null;
  addedAt: string;
  updatedAt: string;
  progress: Progress;
};

export type DocumentStatus = "STORED" | "PROCESSING" | "READY" | "FAILED";
export type DocumentItem = {
  id: string;
  bookId?: string | null;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  failureReason?: string | null;
  errorMessage?: string | null;
  chunkCount: number;
  createdAt: string;
};

export type RagSource = {
  label: string;
  source: string;
  documentId?: string | null;
  chunkIndex?: number | null;
  score?: number | null;
  snippet: string;
};
export type RagAnswer = { answer: string; sources: RagSource[] };

export type SummaryType = "TLDR" | "SHORT" | "TAKEAWAYS";

export type Recommendation = { book: Book; score: number; reasons: string[] };

export type CurrentlyReading = {
  bookId: string;
  title: string;
  coverUrl?: string | null;
  currentPage: number;
  pageCount?: number | null;
  percentage: number;
};
export type RecentActivity = {
  bookId: string;
  title: string;
  status: LibraryStatus;
  percentage: number;
  updatedAt: string;
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
  recentActivity: RecentActivity[];
};

export type User = {
  id: string;
  email: string;
  displayName: string;
  role: "USER" | "ADMIN";
  createdAt: string;
};
export type AuthResponse = { accessToken: string; refreshToken: string; user: User };

export type UsageBreakdown = {
  key: string;
  requests: number;
  failures: number;
  inputTokens: number;
  outputTokens: number;
};
export type AiUsage = {
  aiEnabled: boolean;
  windowDays: number;
  requests: number;
  successful: number;
  failed: number;
  inputTokens: number;
  outputTokens: number;
  averageLatencyMs: number;
  byModel: UsageBreakdown[];
  byOperation: UsageBreakdown[];
};

export type ChatTurn = { role: "USER" | "ASSISTANT"; text: string };
