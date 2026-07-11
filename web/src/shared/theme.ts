import { useEffect, useState } from 'react';
import { type Role } from './role';

export type Theme = Role;

export function getRoleFromPath(path: string): Role {
  if (path.startsWith('/admin')) return 'admin';
  if (path.startsWith('/parent')) return 'parent';
  return 'child';
}

export function setDocumentTheme(role: Role): void {
  document.documentElement.setAttribute('data-role', role);
  document.documentElement.style.colorScheme = role === 'admin' ? 'light' : 'light';
}

export function useTheme(role: Role): Theme {
  const [theme] = useState<Theme>(() => role);

  useEffect(() => {
    setDocumentTheme(role);
    return () => {
      // Keep the theme set on the document to avoid flash on unmount.
    };
  }, [role]);

  return theme;
}

export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)')?.matches ?? false;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (!media) return;
    const handler = (event: MediaQueryListEvent) => setReduced(event.matches);
    media.addEventListener('change', handler);
    return () => media.removeEventListener('change', handler);
  }, []);

  return reduced;
}

export function useOnline(): boolean {
  const [online, setOnline] = useState(() => {
    if (typeof navigator === 'undefined') return true;
    return navigator.onLine;
  });

  useEffect(() => {
    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  return online;
}

export function useLowPerformance(): boolean {
  interface ConnectionInfo extends EventTarget {
    saveData?: boolean;
    effectiveType?: string;
  }
  const [lowPerf, setLowPerf] = useState(() => {
    if (typeof navigator === 'undefined' || !('deviceMemory' in navigator)) return false;
    const memory = (navigator as Navigator & { deviceMemory?: number }).deviceMemory;
    return memory !== undefined && memory <= 4;
  });

  useEffect(() => {
    const connection =
      (navigator as Navigator & { connection?: ConnectionInfo }).connection ?? null;
    if (!connection) return;

    const update = () => {
      setLowPerf(connection.saveData === true || connection.effectiveType === '2g');
    };
    update();
    connection.addEventListener('change', update);
    return () => connection.removeEventListener('change', update);
  }, []);

  return lowPerf;
}

export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)')?.matches ?? false;
}

export function isLowPerformance(): boolean {
  if (typeof navigator === 'undefined') return false;
  const memory = (navigator as Navigator & { deviceMemory?: number }).deviceMemory;
  const connection =
    (navigator as Navigator & { connection?: { saveData?: boolean; effectiveType?: string } })
      .connection ?? null;
  return (
    (memory !== undefined && memory <= 4) ||
    connection?.saveData === true ||
    connection?.effectiveType === '2g'
  );
}
