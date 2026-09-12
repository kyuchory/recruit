"use client";

import { FormEvent, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { PENDING_URL_KEY } from "@/lib/auth-flow";
import type { LinkImportResponse, MeResponse } from "@/lib/types";
import { Button, inputClass } from "@/components/ui";

const FEATURE_BANNERS = [
  { src: "/promo/post-job.png", alt: "채용공고를 등록하고, 채용 공고를 관리하세요" },
  { src: "/promo/app-notify.png", alt: "앱을 설치하시면 등록된 일정에 맞추어 알림을 받아볼 수 있어요" },
] as const;

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
    <div className="min-h-screen bg-gray-50">
      <header className="border-b border-gray-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-3">
          <Image src="/logo.png" alt="InBOX" width={104} height={38} priority className="h-8 w-auto" />
          <div className="flex items-center gap-4 text-sm">
            <Link href="/login" className="text-gray-600 hover:text-gray-900">로그인</Link>
            <Link href="/app" className="rounded-md bg-brand px-4 py-2 font-medium text-brand-foreground hover:bg-brand-dark">
              내 Inbox
            </Link>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6">
        <section className="pt-12">
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            {FEATURE_BANNERS.map((banner) => (
              <div key={banner.src} className="relative aspect-[2/1] overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
                <Image src={banner.src} alt={banner.alt} fill sizes="(min-width: 640px) 50vw, 100vw" className="object-cover" />
              </div>
            ))}
          </div>
        </section>

        <section className="flex flex-col justify-center py-16">
          <p className="text-sm font-semibold text-brand">공고에서 일정까지 한 번에</p>
          <h1 className="mt-3 max-w-3xl text-4xl font-bold leading-tight text-gray-900 sm:text-5xl">
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
              className={`${inputClass} focus:border-brand focus:ring-1 focus:ring-brand`}
            />
            <Button type="submit" variant="brand" className="whitespace-nowrap px-5" disabled={busy}>
              {busy ? "확인 중" : "Inbox에 추가"}
            </Button>
          </form>
          {notice && <p className="mt-3 text-sm text-red-600">{notice}</p>}
          <p className="mt-3 text-xs text-gray-500">로그인이 필요하면 URL을 보관한 뒤 로그인 후 자동으로 등록합니다.</p>
        </section>
      </main>
    </div>
  );
}
