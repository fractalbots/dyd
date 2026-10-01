import { useCallback, useEffect, useState } from "react";
import type { Session } from "@supabase/supabase-js";
import { check, supabase, type Profile } from "./supabase";

/** Carga datos asíncronos con estado de carga, error y recarga. */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(fn, deps);
  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setData(await load());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [load]);
  useEffect(() => { void reload(); }, [reload]);
  return { data, error, loading, reload };
}

export function useSession() {
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  const [profile, setProfile] = useState<Profile | null>(null);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session));
    const { data } = supabase.auth.onAuthStateChange((_e, s) => setSession(s));
    return () => data.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (!session) { setProfile(null); return; }
    void (async () => {
      try {
        setProfile(check(await supabase.from("profiles").select("*").eq("id", session.user.id).single()) as Profile);
      } catch {
        setProfile(null);
      }
    })();
  }, [session]);

  return { session, profile };
}
