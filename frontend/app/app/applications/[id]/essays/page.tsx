"use client";

import { use } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { EssaySection } from "@/components/essay-section";
import { api } from "@/lib/api";
import type { ApplicationResponse } from "@/lib/types";

export default function ApplicationEssaysPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const application = useQuery({
    queryKey: ["application", id],
    queryFn: () => api.get<ApplicationResponse>(`/api/v1/applications/${id}`),
  });

  if (application.isLoading) return <p className="text-sm text-gray-400">불러오는 중…</p>;
  if (!application.data) return <p className="text-sm text-red-600">지원 건을 찾을 수 없습니다.</p>;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link href={`/app/applications/${id}`} className="text-sm text-brand hover:underline">← 전형 관리</Link>
        <Link href="/app" className="text-sm text-gray-500 hover:underline">지원현황</Link>
      </div>
      <header className="rounded-lg border border-gray-200 bg-white p-4">
        <p className="text-xs font-medium text-gray-400">자기소개서 작성</p>
        <h1 className="mt-1 text-xl font-semibold">{application.data.companyName ?? "회사명 미정"}</h1>
        <p className="mt-1 text-sm text-gray-500">{application.data.positionTitle ?? "직무 미정"}</p>
      </header>
      <EssaySection applicationId={id} />
    </div>
  );
}
