"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { PENDING_URL_KEY } from "@/lib/auth-flow";
import {
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
  ApplicationResponse,
  EventResponse,
  EVENT_LABELS,
  EventType,
  EssayProgressResponse,
  LinkImportResponse,
  PageResponse,
  SummaryResponse,
} from "@/lib/types";
import { Badge, Button, inputClass } from "@/components/ui";
import { ScheduleCalendar } from "@/components/schedule-calendar";
import { Modal } from "@/components/modal";

const FILTERS = ["전체", "오늘 일정", "이번주 일정", "지원예정", "진행중", "확인필요"] as const;
type Filter = (typeof FILTERS)[number];
type StatusFilter = ApplicationStatus | "ALL";
type ScheduleSort = EventType | "UPDATED";
type SortDirection = "ASC" | "DESC";

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

// The registration/app-install banners moved to the "/" landing page; the
// dashboard keeps only the two feature banners relevant once you're inside the app.
const PROMO_BANNERS = [
  { src: "/promo/career-prep.png", alt: "지금 당신의 커리어를 준비하세요" },
  { src: "/promo/calendar.png", alt: "지원 일정을 캘린더로 확인하세요" },
] as const;

const SORT_LABELS: Record<ScheduleSort, string> = {
  UPDATED: "최근 수정순",
  DOCUMENT_DEADLINE: "서류 날짜순",
  NCS: "NCS 날짜순",
  CODING_TEST: "코테 날짜순",
  AI_ASSESSMENT: "AI 역량검사 날짜순",
  INTERVIEW_1: "1차 면접 날짜순",
  INTERVIEW_2: "2차 면접 날짜순",
  FINAL_INTERVIEW: "최종 면접 날짜순",
  RESULT_ANNOUNCEMENT: "결과 발표 날짜순",
  ORIENTATION: "OT 날짜순",
  CUSTOM: "기타 일정 날짜순",
};

function eventTime(event: EventResponse): number | null {
  const value = event.scheduledAt ?? event.startAt ?? event.endAt
    ?? (event.scheduledDate ? `${event.scheduledDate}T00:00:00` : null);
  if (!value) return null;
  const timestamp = Date.parse(value);
  return Number.isNaN(timestamp) ? null : timestamp;
}

function sortTime(events: EventResponse[], type: EventType): number | null {
  const times = events
    .filter((event) => event.type === type && !["COMPLETED", "CANCELLED"].includes(event.status))
    .map(eventTime)
    .filter((value): value is number => value !== null);
  return times.length > 0 ? Math.min(...times) : null;
}

function fmt(dt: string | null, date: string | null): string {
  if (dt) return new Date(dt).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
  if (date) return `${date} · 시간확인`;
  return "미정";
}

function dDay(event: EventResponse): { label: string; className: string } | null {
  if (event.daysUntil === null) return null;
  const days = event.daysUntil;
  const label = days === 0 ? "D-DAY" : days > 0 ? `D-${days}` : `D+${Math.abs(days)}`;
  const className = days < 0
    ? "bg-gray-100 text-gray-500"
    : days <= 3
      ? "bg-red-100 text-red-700"
      : days <= 7
        ? "bg-amber-100 text-amber-700"
        : "bg-green-100 text-green-700";
  return { label, className };
}

function toDateTimeLocal(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const STATUS_SELECT_CLASS: Record<string, string> = {
  gray: "bg-gray-100 text-gray-700",
  green: "bg-green-100 text-green-800",
  blue: "bg-blue-100 text-blue-800",
  amber: "bg-amber-100 text-amber-800",
  red: "bg-red-100 text-red-800",
};

function StatusCell({ application, onChanged }: { application: ApplicationResponse; onChanged: () => void }) {
  const update = useMutation({
    mutationFn: (status: ApplicationStatus) =>
      api.patch(`/api/v1/applications/${application.id}`, { expectedVersion: application.version, status }),
    onSuccess: onChanged,
  });
  return (
    <td className="px-3 py-2">
      <select
        value={application.status}
        onChange={(event) => update.mutate(event.target.value as ApplicationStatus)}
        disabled={update.isPending}
        className={`rounded border-0 px-1.5 py-0.5 text-xs font-medium ${STATUS_SELECT_CLASS[STATUS_TONE[application.status]]}`}
      >
        {Object.entries(APPLICATION_STATUS_LABELS).map(([value, label]) => (
          <option key={value} value={value}>{label}</option>
        ))}
      </select>
      {application.reviewStatus === "PENDING" && (
        <span className="ml-1">
          <Badge tone="amber">확인필요</Badge>
        </span>
      )}
      {update.isError && <div className="mt-0.5 text-[10px] text-red-600">{(update.error as Error).message}</div>}
    </td>
  );
}

function EventCell({
  applicationId,
  type,
  events,
  onChanged,
}: {
  applicationId: string;
  type: EventType;
  events: EventResponse[];
  onChanged: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");

  const matching = useMemo(() => events.filter((event) => event.type === type), [events, type]);
  const next = useMemo(() => {
    const upcoming = [...matching]
      .filter((event) => !["COMPLETED", "CANCELLED"].includes(event.status))
      .sort((left, right) => (eventTime(left) ?? Number.MAX_SAFE_INTEGER) - (eventTime(right) ?? Number.MAX_SAFE_INTEGER));
    return upcoming[0] ?? matching[0];
  }, [matching]);

  const save = useMutation({
    mutationFn: () => {
      if (!value) throw new Error("날짜와 시간을 선택해 주세요.");
      const iso = new Date(value).toISOString();
      if (next) {
        return api.patch(`/api/v1/events/${next.id}`, {
          expectedVersion: next.version,
          scheduleKind: "EXACT",
          scheduledAt: iso,
          startAt: iso,
        });
      }
      return api.post(`/api/v1/applications/${applicationId}/events`, {
        type,
        scheduleKind: "EXACT",
        scheduledAt: iso,
        startAt: iso,
      });
    },
    onSuccess: () => {
      setEditing(false);
      onChanged();
    },
  });

  if (editing) {
    return (
      <td className="px-2 py-1.5 text-center">
        <div className="flex items-center justify-center gap-1">
          <input
            type="datetime-local"
            autoFocus
            defaultValue={toDateTimeLocal(next?.scheduledAt ?? next?.startAt ?? null)}
            onChange={(event) => setValue(event.target.value)}
            className="w-36 rounded border border-gray-300 px-1 py-0.5 text-[11px] focus:border-brand focus:outline-none"
          />
          <button
            type="button"
            onClick={() => save.mutate()}
            disabled={save.isPending}
            aria-label={`${EVENT_LABELS[type]} 일정 저장`}
            className="text-brand hover:opacity-70 disabled:opacity-40"
          >
            ✓
          </button>
          <button
            type="button"
            onClick={() => setEditing(false)}
            aria-label={`${EVENT_LABELS[type]} 일정 편집 취소`}
            className="text-gray-400 hover:text-gray-600"
          >
            ✕
          </button>
        </div>
        {save.isError && <div className="mt-0.5 text-[10px] text-red-600">{(save.error as Error).message}</div>}
      </td>
    );
  }

  if (!next) {
    return (
      <td className="px-2 py-2 text-center">
        <button
          type="button"
          onClick={() => setEditing(true)}
          title={`${EVENT_LABELS[type]} 일정 추가`}
          className="text-gray-300 hover:text-brand"
        >
          +
        </button>
      </td>
    );
  }

  const remaining = ["COMPLETED", "CANCELLED"].includes(next.status) ? null : dDay(next);
  return (
    <td className="px-2 py-2 text-center text-xs">
      <button type="button" onClick={() => setEditing(true)} className="hover:text-brand hover:underline">
        {fmt(next.scheduledAt ?? next.startAt, next.scheduledDate)}
      </button>
      {remaining && <span className={`ml-1 inline-flex rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${remaining.className}`}>{remaining.label}</span>}
      {matching.length > 1 && <span className="ml-1 text-gray-400">+{matching.length - 1}</span>}
    </td>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const invalidateApplication = (applicationId: string) => {
    qc.invalidateQueries({ queryKey: ["applications"] });
    qc.invalidateQueries({ queryKey: ["summary"] });
    qc.invalidateQueries({ queryKey: ["events", applicationId] });
  };
  const [url, setUrl] = useState("");
  const [showManual, setShowManual] = useState(false);
  const [manualCompany, setManualCompany] = useState("");
  const [manualPosition, setManualPosition] = useState("");
  const [manualUrl, setManualUrl] = useState("");
  const [filter, setFilter] = useState<Filter>("전체");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [sortBy, setSortBy] = useState<ScheduleSort>("UPDATED");
  const [sortDirection, setSortDirection] = useState<SortDirection>("DESC");
  const [view, setView] = useState<"list" | "calendar">("list");
  const [notice, setNotice] = useState<string | null>(null);
  const resumePendingUrl = useRef(false);

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
      if (resumePendingUrl.current) {
        sessionStorage.removeItem(PENDING_URL_KEY);
        resumePendingUrl.current = false;
        router.replace("/app");
      }
      setNotice(r.duplicate ? "이미 저장된 공고입니다." : "저장했습니다. 분석을 시작합니다.");
      qc.invalidateQueries({ queryKey: ["applications"] });
      qc.invalidateQueries({ queryKey: ["summary"] });
    },
    onError: (e: Error) => setNotice(e.message),
  });

  const applications = useMemo(() => appsQuery.data?.items ?? [], [appsQuery.data]);
  const eventQueries = useQueries({
    queries: applications.map((application) => ({
      queryKey: ["events", application.id],
      queryFn: () => api.get<EventResponse[]>(`/api/v1/applications/${application.id}/events`),
    })),
  });
  const eventsByApplication = useMemo(() => {
    const result = new Map<string, EventResponse[]>();
    applications.forEach((application, index) => {
      result.set(application.id, eventQueries[index]?.data ?? []);
    });
    return result;
  }, [applications, eventQueries]);
  const essayProgressQuery = useQuery({
    queryKey: ["essay-progress"],
    queryFn: () => api.get<EssayProgressResponse[]>("/api/v1/essay-progress"),
  });
  const essaysByApplication = useMemo(() => {
    return new Map((essayProgressQuery.data ?? []).map((progress) => [progress.applicationId, progress]));
  }, [essayProgressQuery.data]);

  const createManual = useMutation({
    mutationFn: () =>
      api.post<ApplicationResponse>("/api/v1/applications/manual", {
        companyName: manualCompany.trim(),
        positionTitle: manualPosition.trim(),
        sourceUrl: manualUrl.trim() || undefined,
      }),
    onSuccess: (application) => {
      setManualCompany("");
      setManualPosition("");
      setManualUrl("");
      qc.invalidateQueries({ queryKey: ["applications"] });
      qc.invalidateQueries({ queryKey: ["summary"] });
      router.push(`/app/applications/${application.id}`);
    },
    onError: (e: Error) => setNotice(e.message),
  });
  const { mutate: saveUrlMutate } = saveUrl;

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const pendingUrl = sessionStorage.getItem(PENDING_URL_KEY);
    if (params.get("resume") === "url" && pendingUrl && !resumePendingUrl.current) {
      resumePendingUrl.current = true;
      setUrl(pendingUrl);
      saveUrlMutate(pendingUrl);
    }
  }, [saveUrlMutate]); // Restore the one-time action after OAuth returns to /app?resume=url.

  const rows = useMemo(() => {
    let filtered = applications;
    const summary = summaryQuery.data;
    switch (filter) {
      case "오늘 일정": {
        const ids = new Set((summary?.today ?? []).map((e) => e.applicationId));
        filtered = filtered.filter((a) => ids.has(a.id));
        break;
      }
      case "이번주 일정": {
        const ids = new Set((summary?.thisWeek ?? []).map((e) => e.applicationId));
        filtered = filtered.filter((a) => ids.has(a.id));
        break;
      }
      case "지원예정":
        filtered = filtered.filter((a) => ["INTERESTED", "PLANNED"].includes(a.status));
        break;
      case "진행중":
        filtered = filtered.filter((a) => ["APPLIED", "IN_PROGRESS"].includes(a.status));
        break;
      case "확인필요":
        filtered = filtered.filter((a) => a.reviewStatus === "PENDING");
        break;
    }

    if (statusFilter !== "ALL") {
      filtered = filtered.filter((application) => application.status === statusFilter);
    }

    return [...filtered].sort((left, right) => {
      if (sortBy === "UPDATED") {
        const comparison = Date.parse(left.updatedAt) - Date.parse(right.updatedAt);
        return sortDirection === "ASC" ? comparison : -comparison;
      }
      const leftTime = sortTime(eventsByApplication.get(left.id) ?? [], sortBy);
      const rightTime = sortTime(eventsByApplication.get(right.id) ?? [], sortBy);
      if (leftTime === null && rightTime === null) return 0;
      if (leftTime === null) return 1;
      if (rightTime === null) return -1;
      const comparison = leftTime - rightTime;
      return sortDirection === "ASC" ? comparison : -comparison;
    });
  }, [applications, summaryQuery.data, filter, statusFilter, sortBy, sortDirection, eventsByApplication]);

  const FILTER_COUNTS: Record<Filter, number | undefined> = {
    "전체": applications.length,
    "오늘 일정": summaryQuery.data?.todayCount,
    "이번주 일정": summaryQuery.data?.thisWeekCount,
    "지원예정": applications.filter((a) => ["INTERESTED", "PLANNED"].includes(a.status)).length,
    "진행중": applications.filter((a) => ["APPLIED", "IN_PROGRESS"].includes(a.status)).length,
    "확인필요": summaryQuery.data?.needsReviewCount,
  };

  return (
    <div>
      <div className="grid grid-cols-2 gap-3">
        {PROMO_BANNERS.map((banner) => (
          <div key={banner.src} className="relative aspect-[2/1] overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
            <Image src={banner.src} alt={banner.alt} fill sizes="50vw" className="object-cover" />
          </div>
        ))}
      </div>
      <div className="mt-5 flex flex-col gap-2 lg:flex-row">
        <form
          className="flex flex-1 gap-2"
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
          <Button type="submit" variant="brand" className="whitespace-nowrap px-4" disabled={saveUrl.isPending}>
            URL 저장
          </Button>
        </form>
        <Button variant="ghost" onClick={() => setShowManual(true)}>
          직접 등록
        </Button>
      </div>
      {notice && <p className="mt-2 text-sm text-gray-500">{notice}</p>}

      <Modal open={showManual} onClose={() => setShowManual(false)} title="지원 건 직접 등록">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setNotice(null);
            if (manualCompany.trim() && manualPosition.trim()) createManual.mutate();
          }}
        >
          <div className="grid gap-2 sm:grid-cols-2">
            <input
              value={manualCompany}
              onChange={(e) => setManualCompany(e.target.value)}
              placeholder="회사명"
              maxLength={200}
              required
              className={inputClass}
            />
            <input
              value={manualPosition}
              onChange={(e) => setManualPosition(e.target.value)}
              placeholder="직무"
              maxLength={300}
              required
              className={inputClass}
            />
            <input
              type="url"
              value={manualUrl}
              onChange={(e) => setManualUrl(e.target.value)}
              placeholder="채용공고 URL (선택)"
              maxLength={4096}
              className="sm:col-span-2 w-full rounded-md border border-gray-300 px-2.5 py-1.5 text-sm focus:border-brand focus:outline-none"
            />
          </div>
          <div className="mt-3 flex items-center gap-2">
            <Button type="submit" variant="brand" disabled={createManual.isPending}>
              {createManual.isPending ? "만드는 중…" : "만들고 일정 추가"}
            </Button>
            <span className="text-xs text-gray-500">생성 후 상세 화면에서 전형 일정을 추가할 수 있습니다.</span>
          </div>
        </form>
      </Modal>

      <div className="mt-5 flex flex-wrap gap-2 text-sm">
        {FILTERS.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={
              "flex items-center gap-1.5 rounded-full px-3 py-1 transition-colors " +
              (filter === f ? "bg-brand text-brand-foreground" : "bg-white text-gray-600 hover:bg-gray-100")
            }
          >
            {f}
            <span
              className={
                "rounded-full px-1.5 text-xs tabular-nums " +
                (filter === f ? "bg-white/20" : "bg-gray-100 text-gray-500")
              }
            >
              {FILTER_COUNTS[f] ?? 0}
            </span>
          </button>
        ))}
      </div>

      <div className="mt-4 flex flex-col gap-2 rounded-xl border border-gray-200 bg-white p-3 shadow-sm lg:flex-row lg:items-center">
        <div className="flex items-center gap-1 rounded-lg border border-gray-200 bg-gray-50 p-1 text-sm">
          <button
            type="button"
            onClick={() => setView("list")}
            className={`rounded-md px-3 py-1.5 transition-colors ${view === "list" ? "bg-brand text-brand-foreground" : "text-gray-500 hover:bg-white"}`}
          >
            리스트
          </button>
          <button
            type="button"
            onClick={() => setView("calendar")}
            className={`rounded-md px-3 py-1.5 transition-colors ${view === "calendar" ? "bg-brand text-brand-foreground" : "text-gray-500 hover:bg-white"}`}
          >
            캘린더
          </button>
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-600">
          <span className="shrink-0">지원 상태</span>
          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
            className={inputClass}
          >
            <option value="ALL">전체 상태</option>
            {Object.entries(APPLICATION_STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        {view === "list" && (
        <label className="flex flex-1 items-center gap-2 text-sm text-gray-600 lg:justify-end">
          <span className="shrink-0">정렬</span>
          <select
            value={sortBy}
            onChange={(event) => {
              const next = event.target.value as ScheduleSort;
              setSortBy(next);
              setSortDirection(next === "UPDATED" ? "DESC" : "ASC");
            }}
            className="rounded-md border border-gray-300 px-2.5 py-1.5 text-sm focus:border-gray-500 focus:outline-none"
          >
            <option value="UPDATED">최근 수정순</option>
            {COLUMN_TYPES.map((type) => (
              <option key={type} value={type}>{SORT_LABELS[type]}</option>
            ))}
          </select>
          <button
            type="button"
            onClick={() => setSortDirection((direction) => direction === "ASC" ? "DESC" : "ASC")}
            className="shrink-0 rounded-md border border-gray-300 px-3 py-1.5 text-gray-700 hover:bg-gray-50"
          >
            {sortBy === "UPDATED"
              ? (sortDirection === "ASC" ? "오래된 수정부터 ↑" : "최근 수정부터 ↓")
              : (sortDirection === "ASC" ? "빠른 날짜부터 ↑" : "늦은 날짜부터 ↓")}
          </button>
        </label>
        )}
        {(filter !== "전체" || statusFilter !== "ALL" || sortBy !== "UPDATED" || sortDirection !== "DESC") && (
          <button
            type="button"
            onClick={() => {
              setFilter("전체");
              setStatusFilter("ALL");
              setSortBy("UPDATED");
              setSortDirection("DESC");
            }}
            className="shrink-0 px-2 py-1.5 text-sm text-gray-500 hover:text-gray-900"
          >
            초기화
          </button>
        )}
      </div>

      {view === "calendar" && (
        <div className="mt-4">
          <ScheduleCalendar applications={rows} eventsByApplication={eventsByApplication} />
        </div>
      )}

      {view === "list" && (
      <div className="mt-4 overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
        <table className="min-w-[960px] w-full text-sm">
          <thead className="border-b border-gray-200 bg-gray-50 text-xs text-gray-500">
            <tr>
              <th className="px-3 py-2 text-left">상태</th>
              <th className="px-3 py-2 text-left">회사 / 직무</th>
              {COLUMN_TYPES.map((t) => (
                <th key={t} className="px-2 py-2">
                  <span className="inline-flex items-center gap-1 whitespace-nowrap">
                    <button
                      type="button"
                      onClick={() => {
                        if (sortBy === t) {
                          setSortDirection((direction) => direction === "ASC" ? "DESC" : "ASC");
                        } else {
                          setSortBy(t);
                          setSortDirection("ASC");
                        }
                      }}
                      aria-label={`${EVENT_LABELS[t]} 날짜순 정렬 방향 전환`}
                      title="클릭할 때마다 정렬 방향 전환"
                      className="hover:text-gray-800"
                    >
                      {EVENT_LABELS[t]}
                    </button>
                    <span className="inline-flex flex-col leading-none">
                      <button
                        type="button"
                        onClick={() => {
                          setSortBy(t);
                          setSortDirection("ASC");
                        }}
                        aria-label={`${EVENT_LABELS[t]} 빠른 날짜부터 정렬`}
                        aria-pressed={sortBy === t && sortDirection === "ASC"}
                        title="빠른 날짜부터"
                        className="flex h-2.5 w-3 items-center justify-center text-[7px] text-gray-400 hover:text-gray-700"
                      >
                        ▲
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setSortBy(t);
                          setSortDirection("DESC");
                        }}
                        aria-label={`${EVENT_LABELS[t]} 늦은 날짜부터 정렬`}
                        aria-pressed={sortBy === t && sortDirection === "DESC"}
                        title="늦은 날짜부터"
                        className="flex h-2.5 w-3 items-center justify-center text-[7px] text-gray-400 hover:text-gray-700"
                      >
                        ▼
                      </button>
                    </span>
                  </span>
                </th>
              ))}
              <th className="px-3 py-2">자기소개서</th>
              <th className="px-3 py-2">관리</th>
            </tr>
          </thead>
          <tbody>
            {appsQuery.isLoading && (
              <tr>
                <td colSpan={9} className="px-3 py-6 text-center text-gray-400">
                  불러오는 중…
                </td>
              </tr>
            )}
            {!appsQuery.isLoading && rows.length === 0 && (
              <tr>
                <td colSpan={9} className="px-3 py-6 text-center text-gray-400">
                  조건에 맞는 공고가 없습니다.
                </td>
              </tr>
            )}
            {rows.map((a) => (
              <tr key={a.id} className="border-b border-gray-100 last:border-0 hover:bg-brand-light/40">
                <StatusCell application={a} onChanged={() => invalidateApplication(a.id)} />
                <td className="px-3 py-2">
                  {a.sourceUrl ? (
                    <a href={a.sourceUrl} target="_blank" rel="noopener noreferrer" className="group inline-block">
                      <div className="font-medium group-hover:text-brand group-hover:underline">
                        {a.companyName ?? "분석 중…"} ↗
                      </div>
                      <div className="text-xs text-gray-500 group-hover:underline">{a.positionTitle ?? ""}</div>
                    </a>
                  ) : (
                    <Link href={`/app/applications/${a.id}`} className="inline-block hover:underline">
                      <div className="font-medium">{a.companyName ?? "분석 중…"}</div>
                      <div className="text-xs text-gray-500">{a.positionTitle ?? ""}</div>
                    </Link>
                  )}
                </td>
                {COLUMN_TYPES.map((type) => (
                  <EventCell
                    key={type}
                    applicationId={a.id}
                    type={type}
                    events={eventsByApplication.get(a.id) ?? []}
                    onChanged={() => invalidateApplication(a.id)}
                  />
                ))}
                <EssayStatusCell applicationId={a.id} progress={essaysByApplication.get(a.id)} />
                <td className="px-3 py-2 text-center">
                  <Link href={`/app/applications/${a.id}`} className="text-xs text-brand hover:underline">
                    수정
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      )}
    </div>
  );
}

function EssayStatusCell({ applicationId, progress }: { applicationId: string; progress?: EssayProgressResponse }) {
  const total = progress?.totalCount ?? 0;
  const completed = progress?.completedCount ?? 0;
  const status = total === 0
    ? { label: "미작성", tone: "gray" }
    : completed === total
      ? { label: "작성됨", tone: "green" }
      : { label: "작성중", tone: "amber" };
  return (
    <td className="px-3 py-2 text-center">
      <Link href={`/app/applications/${applicationId}/essays`} className="inline-flex flex-col items-center gap-0.5 hover:opacity-75">
        <Badge tone={status.tone}>{status.label}</Badge>
        {total > 0 && <span className="text-[10px] text-gray-400">{completed}/{total}</span>}
      </Link>
    </td>
  );
}
