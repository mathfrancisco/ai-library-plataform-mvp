"use client";
import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { api, isNotFound } from "@/lib/api";
import { useSignedIn } from "@/lib/auth";
import type {
  AiUsage,
  Book,
  DocumentItem,
  LibraryItem,
  LibraryItemSummary,
  LibraryStatus,
  Progress,
  User,
} from "@/types/api";

export type LibraryPatch = { status?: LibraryStatus; favorite?: boolean; rating?: number };

/** Everything that shows shelf state must refresh after a shelf or progress change. */
export function invalidateShelf(qc: QueryClient, bookId?: string) {
  qc.invalidateQueries({ queryKey: ["library"] });
  qc.invalidateQueries({ queryKey: ["dashboard"] });
  qc.invalidateQueries({ queryKey: ["recommendations"] });
  if (bookId) {
    qc.invalidateQueries({ queryKey: ["library-item", bookId] });
    qc.invalidateQueries({ queryKey: ["progress", bookId] });
  }
}

export function useBook(id: string) {
  return useQuery({ queryKey: ["book", id], queryFn: () => api<Book>(`/api/books/${id}`), retry: false });
}

export function useLibrary(status?: LibraryStatus | "") {
  const signedIn = useSignedIn();
  return useQuery({
    queryKey: ["library", status ?? ""],
    queryFn: () => api<LibraryItemSummary[]>(`/api/library${status ? `?status=${status}` : ""}`),
    enabled: signedIn,
  });
}

/** null when the book is not on the user's shelf. */
export function useLibraryItem(bookId: string) {
  const signedIn = useSignedIn();
  return useQuery({
    queryKey: ["library-item", bookId],
    enabled: signedIn,
    queryFn: async () => {
      try {
        return await api<LibraryItem>(`/api/library/books/${bookId}`);
      } catch (e) {
        if (isNotFound(e)) return null;
        throw e;
      }
    },
  });
}

/** POST adds (idempotent) when the book is not on the shelf; PATCH updates otherwise. */
export function useUpsertLibraryItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ bookId, patch, onShelf }: { bookId: string; patch: LibraryPatch; onShelf: boolean }) =>
      api<LibraryItem>(`/api/library/books/${bookId}`, {
        method: onShelf ? "PATCH" : "POST",
        body: JSON.stringify(patch),
      }),
    onSuccess: (_, { bookId }) => invalidateShelf(qc, bookId),
  });
}

export function useRemoveLibraryItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (bookId: string) => api(`/api/library/books/${bookId}`, { method: "DELETE" }),
    onSuccess: (_, bookId) => invalidateShelf(qc, bookId),
  });
}

export function useProgress(bookId: string) {
  const signedIn = useSignedIn();
  return useQuery({
    queryKey: ["progress", bookId],
    enabled: signedIn,
    queryFn: () => api<Progress>(`/api/reading/${bookId}`),
  });
}

export function useSaveProgress(bookId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { currentPage?: number; percentage?: number; notes?: string }) =>
      api<Progress>(`/api/reading/${bookId}`, { method: "PUT", body: JSON.stringify(body) }),
    onSuccess: (p) => {
      qc.setQueryData(["progress", bookId], p);
      invalidateShelf(qc, bookId);
    },
  });
}

/** Polls only while a document is still being indexed. */
export function useDocuments() {
  const signedIn = useSignedIn();
  return useQuery({
    queryKey: ["documents"],
    enabled: signedIn,
    queryFn: () => api<DocumentItem[]>("/api/documents"),
    refetchInterval: (q) =>
      q.state.data?.some((d) => d.status === "STORED" || d.status === "PROCESSING") ? 3000 : false,
  });
}

export function useUploadDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ file, bookId }: { file: File; bookId?: string }) => {
      const form = new FormData();
      form.append("file", file);
      return api<DocumentItem>(`/api/documents${bookId ? `?bookId=${bookId}` : ""}`, {
        method: "POST",
        body: form,
      });
    },
    onSuccess: (_, { bookId }) => {
      qc.invalidateQueries({ queryKey: ["documents"] });
      if (bookId) invalidateShelf(qc, bookId);
    },
  });
}

export function useMe() {
  const signedIn = useSignedIn();
  return useQuery({ queryKey: ["me"], enabled: signedIn, queryFn: () => api<User>("/api/auth/me") });
}

export function useAiUsage() {
  const signedIn = useSignedIn();
  return useQuery({
    queryKey: ["ai-usage"],
    enabled: signedIn,
    queryFn: () => api<AiUsage>("/api/ai/usage"),
  });
}
