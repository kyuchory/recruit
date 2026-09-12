"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { PENDING_URL_KEY } from "@/lib/auth-flow";
import type { LinkImportResponse, MeResponse } from "@/lib/types";
import { Button, inputClass } from "@/components/ui";

export default function Home() {
  const router = useRouter();
  const [url, setUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const pendingUrl = url.trim();
    if (!pendingUrl) return;
    setBusy(true);
    setNotice(null);
    try {
      await api.get<MeResponse>("/api/v1/me");
      const saved = await api.post<LinkImportResponse>(
        "/api/v1/links",
        { url: pendingUrl },
        { "Idempotency-Key": crypto.randomUUID() },
      );
      router.push(`/app/applications/${saved.applicationId}`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        sessionStorage.setItem(PENDING_URL_KEY, pendingUrl);
        router.push(`/login?returnTo=${encodeURIComponent("/app?resume=url")}`);
      } else {
        setNotice(error instanceof Error ? error.message : "요청을 처리하지 못했습니다.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-5xl flex-col px-6 py-8">
      <header className="flex items-center justify-between">
        <span className="text-lg font-semibold">채용공고 Inbox</span>
        <div className="flex items-center gap-4 text-sm">
          <Link href="/login" className="text-gray-600 hover:text-gray-900">로그인</Link>
          <Link href="/app" className="rounded-md bg-gray-900 px-4 py-2 text-white">내 Inbox</Link>
        </div>
      </header>

      <section className="flex flex-1 flex-col justify-center py-20">
        <p className="text-sm font-medium text-blue-600">공고에서 일정까지 한 번에</p>
        <h1 className="mt-3 max-w-3xl text-4xl font-bold leading-tight sm:text-5xl">
          놓치면 아쉬운 채용 일정을<br />내 Inbox에 모아보세요.
        </h1>
        <p className="mt-5 max-w-2xl text-gray-600">
          채용공고 URL을 저장하고 서류, 코딩테스트, 면접 일정을 한곳에서 관리합니다.
        </p>
        <form onSubmit={submit} className="mt-8 flex max-w-3xl flex-col gap-2 sm:flex-row">
          <input
            type="url"
            required
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            placeholder="채용공고 URL을 붙여넣으세요"
            className={inputClass}
          />
          <Button type="submit" disabled={busy}>{busy ? "확인 중" : "Inbox에 추가"}</Button>
        </form>
        {notice && <p className="mt-3 text-sm text-red-600">{notice}</p>}
        <p className="mt-3 text-xs text-gray-500">로그인이 필요하면 URL을 보관한 뒤 로그인 후 자동으로 등록합니다.</p>
      </section>
    </main>
  );
}
