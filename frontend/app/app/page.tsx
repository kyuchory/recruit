"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import {
  ApplicationResponse,
  EventResponse,
  EVENT_LABELS,
  EventType,
  LinkImportResponse,
  PageResponse,
  SummaryResponse,
} from "@/lib/types";
import { Badge, Button, inputClass } from "@/components/ui";

const FILTERS = ["전체", "오늘 일정", "이번주 일정", "지원예정", "진행중", "확인필요"] as const;
type Filter = (typeof FILTERS)[number];

const STATUS_TONE: Record<string, string> = {
  INTERESTED: "gray",
  PLANNED: "blue",
  APPLIED: "blue",
  IN_PROGRESS: "amber",
  ACCEPTED: "green",
  REJECTED: "red",
  WITHDRAWN: "gray",
};

const COLUMN_TYPES: EventType[] = [
  "DOCUMENT_DEADLINE",
  "NCS",
  "CODING_TEST",
  "INTERVIEW_1",
  "INTERVIEW_2",
];

function fmt(dt: string | null, date: string | null): string {
  if (dt) return new Date(dt).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
  if (date) return `${date} · 시간확인`;
  return "미정";
}

function EventCells({ applicationId }: { applicationId: string }) {
  const { data } = useQuery({
    queryKey: ["events", applicationId],
    queryFn: () => api.get<EventResponse[]>(`/api/v1/applications/${applicationId}/events`),
  });
  const byType = useMemo(() => {
    const m = new Map<EventType, EventResponse[]>();
    (data ?? []).forEach((e) => {
      const arr = m.get(e.type) ?? [];
      arr.push(e);
      m.set(e.type, arr);
    });
    return m;
  }, [data]);

  return (
    <>
      {COLUMN_TYPES.map((t) => {
        const list = byType.get(t) ?? [];
        if (list.length === 0) return <td key={t} className="px-2 py-2 text-center text-gray-300">-</td>;
        const next = list.find((e) => e.status !== "COMPLETED") ?? list[0];
        return (
          <td key={t} className="px-2 py-2 text-center text-xs">
            {fmt(next.scheduledAt ?? next.startAt, next.scheduledDate)}
            {list.length > 1 && <span className="ml-1 text-gray-400">+{list.length - 1}</span>}
          </td>
        );
      })}
    </>
  );
}

export default function DashboardPage() {
  const qc = useQueryClient();
  const [url, setUrl] = useState("");
  const [filter, setFilter] = useState<Filter>("전체");
  const [notice, setNotice] = useState<string | null>(null);

  const appsQuery = useQuery({
    queryKey: ["applications"],
    queryFn: () => api.get<PageResponse<ApplicationResponse>>("/api/v1/applications?size=100"),
  });

  const summaryQuery = useQuery({
    queryKey: ["summary"],
    queryFn: () => api.get<SummaryResponse>("/api/v1/summary"),
  });

  const saveUrl = useMutation({
    mutationFn: (u: string) =>
      api.post<LinkImportResponse>("/api/v1/links", { url: u }, { "Idempotency-Key": crypto.randomUUID() }),
    onSuccess: (r) => {
      setUrl("");
      setNotice(r.duplicate ? "이미 저장된 공고입니다." : "저장했습니다. 분석을 시작합니다.");
      qc.invalidateQueries({ queryKey: ["applications"] });
      qc.invalidateQueries({ queryKey: ["summary"] });
    },
    onError: (e: Error) => setNotice(e.message),
  });

  const rows = useMemo(() => {
    const all = appsQuery.data?.items ?? [];
    const summary = summaryQuery.data;
    switch (filter) {
      case "오늘 일정": {
        const ids = new Set((summary?.today ?? []).map((e) => e.applicationId));
        return all.filter((a) => ids.has(a.id));
      }
      case "이번주 일정": {
        const ids = new Set((summary?.thisWeek ?? []).map((e) => e.applicationId));
        return all.filter((a) => ids.has(a.id));
      }
      case "지원예정":
        return all.filter((a) => ["INTERESTED", "PLANNED"].includes(a.status));
      case "진행중":
        return all.filter((a) => ["APPLIED", "IN_PROGRESS"].includes(a.status));
      case "확인필요":
        return all.filter((a) => a.reviewStatus === "PENDING");
      default:
        return all;
    }
  }, [appsQuery.data, summaryQuery.data, filter]);

  return (
    <div>
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (url.trim()) saveUrl.mutate(url.trim());
        }}
      >
        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="채용공고 URL 붙여넣기"
          className={inputClass}
        />
        <Button type="submit" disabled={saveUrl.isPending}>
          저장
        </Button>
      </form>
      {notice && <p className="mt-2 text-sm text-gray-500">{notice}</p>}

      <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
        {(
          [
            ["오늘 일정", summaryQuery.data?.todayCount, "오늘 일정" as Filter],
            ["이번 주 일정", summaryQuery.data?.thisWeekCount, "이번주 일정" as Filter],
            ["확인 필요", summaryQuery.data?.needsReviewCount, "확인필요" as Filter],
          ] as const
        ).map(([label, count, target]) => (
          <button
            key={label}
            onClick={() => setFilter((f) => (f === target ? "전체" : target))}
            className={
              "rounded-lg border bg-white px-4 py-3 text-left transition hover:border-gray-400 " +
              (filter === target ? "border-gray-900" : "border-gray-200")
            }
          >
            <div className="text-xs text-gray-500">{label}</div>
            <div className="mt-1 text-2xl font-semibold tabular-nums">
              {summaryQuery.isLoading ? "–" : count ?? 0}
            </div>
          </button>
        ))}
      </div>

      <div className="mt-5 flex flex-wrap gap-2 text-sm">
        {FILTERS.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={
              "rounded-full px-3 py-1 " +
              (filter === f ? "bg-gray-900 text-white" : "bg-white text-gray-600 hover:bg-gray-100")
            }
          >
            {f}
          </button>
        ))}
      </div>

      <div className="mt-4 overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="border-b border-gray-200 bg-gray-50 text-xs text-gray-500">
            <tr>
              <th className="px-3 py-2 text-left">상태</th>
              <th className="px-3 py-2 text-left">회사 / 직무</th>
              {COLUMN_TYPES.map((t) => (
                <th key={t} className="px-2 py-2">
                  {EVENT_LABELS[t]}
                </th>
              ))}
              <th className="px-3 py-2">원본</th>
            </tr>
          </thead>
          <tbody>
            {appsQuery.isLoading && (
              <tr>
                <td colSpan={8} className="px-3 py-6 text-center text-gray-400">
                  불러오는 중…
                </td>
              </tr>
            )}
            {!appsQuery.isLoading && rows.length === 0 && (
              <tr>
                <td colSpan={8} className="px-3 py-6 text-center text-gray-400">
                  아직 저장한 공고가 없습니다.
                </td>
              </tr>
            )}
            {rows.map((a) => (
              <tr key={a.id} className="border-b border-gray-100 last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2">
                  <Badge tone={STATUS_TONE[a.status]}>{a.status}</Badge>
                  {a.reviewStatus === "PENDING" && (
                    <span className="ml-1">
                      <Badge tone="amber">확인필요</Badge>
                    </span>
                  )}
                </td>
                <td className="px-3 py-2">
                  <Link href={`/app/applications/${a.id}`} className="font-medium hover:underline">
                    {a.companyName ?? "분석 중…"}
                  </Link>
                  <div className="text-xs text-gray-500">{a.positionTitle ?? ""}</div>
                </td>
                <EventCells applicationId={a.id} />
                <td className="px-3 py-2 text-center">
                  <Link href={`/app/applications/${a.id}`} className="text-xs text-blue-600 hover:underline">
                    열기
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
