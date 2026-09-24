import { useEffect, useState } from 'react';
import { api } from './api';
import type { Language, LanguageView } from './types';

// USSD languages are data (Settings > Languages): loaded once per page load and shared by all pages.
let cache: LanguageView[] | null = null;
let pending: Promise<LanguageView[]> | null = null;
const listeners = new Set<(l: LanguageView[]) => void>();

function load(force = false): Promise<LanguageView[]> {
  if (!force && cache) return Promise.resolve(cache);
  if (!force && pending) return pending;
  pending = api.get<LanguageView[]>('/api/admin/languages').then((l) => {
    cache = l;
    pending = null;
    listeners.forEach((fn) => fn(l));
    return l;
  }).catch((e) => { pending = null; throw e; });
  return pending;
}

/** Reloads the list everywhere (after adding a language or saving a translation). */
export function refreshLanguages() {
  return load(true).catch(() => cache ?? []);
}

export function useLanguages() {
  const [languages, setLanguages] = useState<LanguageView[]>(cache ?? []);
  const [error, setError] = useState('');
  useEffect(() => {
    listeners.add(setLanguages);
    load().then(setLanguages).catch((e) => setError((e as Error).message));
    return () => { listeners.delete(setLanguages); };
  }, []);
  const find = (code?: Language | null) => languages.find((l) => l.code === code);
  return {
    languages,
    error,
    find,
    /** English name for the portal, e.g. "Kinyarwanda", falling back to the code. */
    label: (code?: Language | null) => (code ? find(code)?.englishName ?? code : '—'),
    ready: (l: LanguageView) => l.active && l.translated >= l.total,
  };
}
