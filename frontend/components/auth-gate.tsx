"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import type { MeResponse } from "@/lib/types";

export function AuthGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const me = useQuery({
    queryKey: ["me"],
    queryFn: () => api.get<MeResponse>("/api/v1/me"),
    retry: false,
  });

  useEffect(() => {
    if (me.error instanceof ApiError && me.error.status === 401) {
      const returnTo = pathname + window.location.search;
      router.replace(`/login?returnTo=${encodeURIComponent(returnTo)}`);
    }
  }, [me.error, pathname, router]);

  if (me.isPending || (me.error instanceof ApiError && me.error.status === 401)) {
    return <div className="py-20 text-center text-sm text-gray-500">로그인 상태를 확인하고 있습니다.</div>;
  }
  if (me.isError) {
    return <div className="py-20 text-center text-sm text-red-600">로그인 상태를 확인하지 못했습니다.</div>;
  }
  return children;
}
