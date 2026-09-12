"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { api, API_BASE_URL } from "@/lib/api";
import { safeReturnTo } from "@/lib/auth-flow";
import type { AuthProvidersResponse } from "@/lib/types";

export default function LoginPage() {
  const [returnTo, setReturnTo] = useState("/app");
  const [error, setError] = useState(false);
  const [providers, setProviders] = useState<AuthProvidersResponse>({ google: true, kakao: false });

  useEffect(() => {
    async function loadLoginState() {
      const params = new URLSearchParams(window.location.search);
      const available = await api.get<AuthProvidersResponse>("/api/v1/auth/providers").catch(() => null);
      setReturnTo(safeReturnTo(params.get("returnTo")));
      setError(params.get("error") === "provider_unavailable");
      if (available) setProviders(available);
    }
    void loadLoginState();
  }, []);

  const startUrl = (provider: "google" | "kakao") =>
    `${API_BASE_URL}/api/v1/auth/start/${provider}?returnTo=${encodeURIComponent(returnTo)}`;

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6">
      <Link href="/" className="mb-8 text-sm text-gray-500 hover:text-brand">← 홈으로</Link>
      <Image src="/logo.png" alt="InBOX" width={104} height={38} priority className="h-9 w-auto" />
      <p className="mt-4 text-sm text-gray-500">
        로그인하면 저장한 공고와 전형 일정을 어느 기기에서든 이어서 관리할 수 있습니다.
      </p>

      {error && <p className="mt-4 rounded-md bg-red-50 p-3 text-sm text-red-700">현재 사용할 수 없는 로그인 방식입니다.</p>}

      <a
        href={providers.google ? startUrl("google") : undefined}
        aria-disabled={!providers.google}
        className={`mt-8 inline-flex items-center justify-center rounded-md border px-4 py-2.5 text-sm font-medium shadow-sm ${
          providers.google ? "border-gray-300 bg-white hover:bg-gray-50" : "cursor-not-allowed border-gray-200 bg-gray-100 text-gray-400"
        }`}
      >
        Google로 계속하기
      </a>
      <a
        href={providers.kakao ? startUrl("kakao") : undefined}
        aria-disabled={!providers.kakao}
        className={`mt-3 inline-flex items-center justify-center rounded-md px-4 py-2.5 text-sm font-medium ${
          providers.kakao ? "bg-[#FEE500] text-black hover:bg-[#f5dc00]" : "cursor-not-allowed bg-gray-100 text-gray-400"
        }`}
      >
        {providers.kakao ? "카카오로 계속하기" : "카카오 로그인 준비 중"}
      </a>

      {process.env.NODE_ENV !== "production" && (
        <div className="mt-6 rounded-md bg-amber-50 p-3 text-xs text-amber-800">
          개발 환경에서는 OAuth 자격증명 없이 개발 사용자로 이용할 수 있습니다.{" "}
          <Link href={returnTo} className="font-medium underline">바로 시작하기</Link>
        </div>
      )}
    </main>
  );
}
