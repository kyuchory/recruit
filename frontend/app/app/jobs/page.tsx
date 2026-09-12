"use client";

import { useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import type { ApplicationResponse } from "@/lib/types";
import {
  COMPANY_TYPES,
  HIRE_TYPES,
  JOB_CATEGORIES,
  JOB_POSTINGS,
  LOCATIONS,
  daysUntil,
  type JobPosting,
} from "@/lib/job-postings";
import { getJobContent } from "@/lib/job-posting-content";
import { Badge, Button } from "@/components/ui";
import { Modal } from "@/components/modal";
import { JobCalendar } from "@/components/job-calendar";

function CompanyAvatar({ name, className = "h-10 w-10 text-sm" }: { name: string; className?: string }) {
  return (
    <span className={`inline-flex shrink-0 items-center justify-center rounded-full bg-brand font-semibold text-brand-foreground ${className}`}>
      {name.slice(0, 1)}
    </span>
  );
}

const PAGE_SIZE = 20;

function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

function dDayLabel(deadline: string | null): { label: string; className: string } | null {
  const days = daysUntil(deadline);
  if (days === null) return null;
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

function fmtDate(value: string | null): string {
  if (!value) return "상시채용";
  return new Date(value).toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" });
}

export default function JobPostingsPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const [view, setView] = useState<"list" | "calendar">("list");
  const [keyword, setKeyword] = useState("");
  const [hireTypes, setHireTypes] = useState<string[]>([]);
  const [companyTypes, setCompanyTypes] = useState<string[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [location, setLocation] = useState("ALL");
  const [excludeClosed, setExcludeClosed] = useState(true);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<JobPosting | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [addedIds, setAddedIds] = useState<Set<string>>(new Set());
  const [scrapped, setScrapped] = useState<Set<string>>(new Set());

  const toggleScrap = (jobId: string) => {
    setScrapped((prev) => {
      const next = new Set(prev);
      if (next.has(jobId)) next.delete(jobId); else next.add(jobId);
      return next;
    });
  };

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLocaleLowerCase("ko-KR");
    return JOB_POSTINGS.filter((job) => {
      if (kw && !`${job.companyName} ${job.positionTitle} ${job.keywords.join(" ")}`.toLocaleLowerCase("ko-KR").includes(kw)) return false;
      if (hireTypes.length > 0 && !hireTypes.includes(job.hireTypeName)) return false;
      if (companyTypes.length > 0 && !companyTypes.includes(job.companyType)) return false;
      if (categories.length > 0 && !categories.includes(job.jobCategoryName)) return false;
      if (location !== "ALL" && job.locationName !== location) return false;
      if (excludeClosed) {
        const days = daysUntil(job.deadline);
        if (!job.isActive || (days !== null && days < 0)) return false;
      }
      return true;
    });
  }, [keyword, hireTypes, companyTypes, categories, location, excludeClosed]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageItems = filtered.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  const resetFilters = () => {
    setKeyword("");
    setHireTypes([]);
    setCompanyTypes([]);
    setCategories([]);
    setLocation("ALL");
    setExcludeClosed(true);
    setPage(0);
  };

  const addToList = useMutation({
    mutationFn: (job: JobPosting) =>
      api.post<ApplicationResponse>("/api/v1/applications/manual", {
        companyName: job.companyName,
        positionTitle: job.positionTitle,
        sourceUrl: job.url,
      }),
    onSuccess: (application, job) => {
      setAddedIds((prev) => new Set(prev).add(job.id));
      qc.invalidateQueries({ queryKey: ["applications"] });
      qc.invalidateQueries({ queryKey: ["summary"] });
      setNotice(`"${job.companyName}" 공고를 내 지원 리스트에 추가했습니다.`);
    },
    onError: (e: Error) => setNotice(e.message),
  });

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold">채용공고</h1>
          <p className="mt-1 text-sm text-gray-500">마음에 드는 공고를 찾아 내 지원 리스트에 바로 추가하세요.</p>
        </div>
        <div className="flex items-center gap-1 rounded-lg border border-gray-200 bg-white p-1 text-sm shadow-sm">
          <button
            type="button"
            onClick={() => setView("list")}
            className={`rounded-md px-3 py-1.5 transition-colors ${view === "list" ? "bg-brand text-brand-foreground" : "text-gray-500 hover:bg-gray-50"}`}
          >
            리스트
          </button>
          <button
            type="button"
            onClick={() => setView("calendar")}
            className={`rounded-md px-3 py-1.5 transition-colors ${view === "calendar" ? "bg-brand text-brand-foreground" : "text-gray-500 hover:bg-gray-50"}`}
          >
            채용달력
          </button>
        </div>
      </div>

      <div className="mt-4 space-y-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <input
          value={keyword}
          onChange={(event) => { setKeyword(event.target.value); setPage(0); }}
          placeholder="기업명, 직무, 키워드로 검색"
          className="w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand"
        />

        <div>
          <p className="mb-1.5 text-xs font-medium text-gray-500">채용형태</p>
          <div className="flex flex-wrap gap-1.5">
            {HIRE_TYPES.map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => { setHireTypes((prev) => toggle(prev, type)); setPage(0); }}
                className={`rounded-full px-3 py-1 text-sm transition-colors ${hireTypes.includes(type) ? "bg-brand text-brand-foreground" : "border border-gray-200 bg-white text-gray-600 hover:bg-gray-50"}`}
              >
                {type}
              </button>
            ))}
          </div>
        </div>

        <div>
          <p className="mb-1.5 text-xs font-medium text-gray-500">기업형태</p>
          <div className="flex flex-wrap gap-1.5">
            {COMPANY_TYPES.map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => { setCompanyTypes((prev) => toggle(prev, type)); setPage(0); }}
                className={`rounded-full px-3 py-1 text-sm transition-colors ${companyTypes.includes(type) ? "bg-brand text-brand-foreground" : "border border-gray-200 bg-white text-gray-600 hover:bg-gray-50"}`}
              >
                {type}
              </button>
            ))}
          </div>
        </div>

        <div>
          <p className="mb-1.5 text-xs font-medium text-gray-500">직무</p>
          <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-sm text-gray-700">
            {JOB_CATEGORIES.map((category) => (
              <label key={category} className="flex items-center gap-1.5">
                <input
                  type="checkbox"
                  checked={categories.includes(category)}
                  onChange={() => { setCategories((prev) => toggle(prev, category)); setPage(0); }}
                />
                {category}
              </label>
            ))}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-gray-600">
            <span className="shrink-0">지역</span>
            <select
              value={location}
              onChange={(event) => { setLocation(event.target.value); setPage(0); }}
              className="rounded-md border border-gray-300 px-2.5 py-1.5 text-sm focus:border-brand focus:outline-none"
            >
              <option value="ALL">전체 지역</option>
              {LOCATIONS.map((loc) => (
                <option key={loc} value={loc}>{loc}</option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2 text-sm text-gray-600">
            <input
              type="checkbox"
              checked={excludeClosed}
              onChange={(event) => { setExcludeClosed(event.target.checked); setPage(0); }}
            />
            마감 공고 제외
          </label>
          <button type="button" onClick={resetFilters} className="text-sm text-gray-500 hover:text-gray-900">
            필터 초기화
          </button>
          <span className="ml-auto text-sm text-gray-500">검색 결과 <strong className="text-gray-900">{filtered.length.toLocaleString()}</strong>건</span>
        </div>
      </div>

      {notice && <p className="mt-3 text-sm text-brand-dark">{notice}</p>}

      {view === "calendar" ? (
        <div className="mt-4">
          <JobCalendar jobs={filtered} onSelect={setSelected} />
        </div>
      ) : (
        <>
          <div className="mt-4 overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
            <table className="min-w-[880px] w-full text-sm">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs text-gray-500">
                <tr>
                  <th className="px-3 py-2 text-left">기업</th>
                  <th className="px-3 py-2 text-left">직무</th>
                  <th className="px-3 py-2 text-left">지역</th>
                  <th className="px-3 py-2 text-left">채용형태</th>
                  <th className="px-3 py-2 text-left">마감일</th>
                  <th className="px-3 py-2">관리</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-3 py-6 text-center text-gray-400">조건에 맞는 공고가 없습니다.</td>
                  </tr>
                )}
                {pageItems.map((job) => {
                  const remaining = dDayLabel(job.deadline);
                  const added = addedIds.has(job.id);
                  return (
                    <tr key={job.id} className="border-b border-gray-100 last:border-0 hover:bg-brand-light/40">
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => toggleScrap(job.id)}
                            aria-label="관심 공고 스크랩"
                            aria-pressed={scrapped.has(job.id)}
                            className={scrapped.has(job.id) ? "text-amber-400" : "text-gray-300 hover:text-amber-400"}
                          >
                            ★
                          </button>
                          <button type="button" onClick={() => setSelected(job)} className="flex items-center gap-2 text-left hover:underline">
                            <CompanyAvatar name={job.companyName} className="h-7 w-7 text-xs" />
                            <div>
                              <div className="flex items-center gap-1.5">
                                <Badge tone="gray">{job.companyType}</Badge>
                                <span className="font-medium">{job.companyName}</span>
                              </div>
                            </div>
                          </button>
                        </div>
                      </td>
                      <td className="px-3 py-2 text-gray-700">{job.positionTitle}</td>
                      <td className="px-3 py-2 text-gray-500">{job.locationName}</td>
                      <td className="px-3 py-2 text-gray-500">{job.hireTypeName}</td>
                      <td className="px-3 py-2">
                        <span className="text-gray-700">{fmtDate(job.deadline)}</span>
                        {remaining && <span className={`ml-1 inline-flex rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${remaining.className}`}>{remaining.label}</span>}
                      </td>
                      <td className="px-3 py-2 text-center">
                        <Button
                          variant={added ? "ghost" : "brand"}
                          className="whitespace-nowrap px-2.5 py-1 text-xs"
                          disabled={added || addToList.isPending}
                          onClick={() => addToList.mutate(job)}
                        >
                          {added ? "추가됨" : "내 리스트에 추가"}
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {pageCount > 1 && (
            <div className="mt-3 flex items-center justify-center gap-2 text-sm">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded-md border border-gray-200 px-3 py-1 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-gray-500">{page + 1} / {pageCount}</span>
              <button
                type="button"
                onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
                disabled={page >= pageCount - 1}
                className="rounded-md border border-gray-200 px-3 py-1 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}

      <Modal open={!!selected} onClose={() => setSelected(null)}>
        {selected && (() => {
          const content = getJobContent(selected);
          const remaining = dDayLabel(selected.deadline);
          const added = addedIds.has(selected.id);
          return (
            <div className="space-y-4 text-sm">
              <div className="flex items-start gap-3">
                <CompanyAvatar name={selected.companyName} />
                <div className="flex-1">
                  <div className="flex items-center gap-1.5">
                    <Badge tone="gray">{selected.companyType}</Badge>
                    <span className="text-sm font-medium text-gray-600">{selected.companyName}</span>
                  </div>
                  <p className="mt-1 text-base font-semibold text-gray-900">{selected.positionTitle}</p>
                </div>
                <button
                  type="button"
                  onClick={() => toggleScrap(selected.id)}
                  aria-label="관심 공고 스크랩"
                  aria-pressed={scrapped.has(selected.id)}
                  className={`text-xl ${scrapped.has(selected.id) ? "text-amber-400" : "text-gray-300 hover:text-amber-400"}`}
                >
                  ★
                </button>
              </div>

              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500">
                <span>게시 {fmtDate(selected.postedAt)}</span>
                <span>·</span>
                <span>조회 {content.viewCount.toLocaleString()}</span>
                <span>·</span>
                <span>스크랩 {content.scrapCount.toLocaleString()}</span>
                <span>·</span>
                <span>마감 {fmtDate(selected.deadline)} ({selected.closeTypeName})</span>
                {remaining && <span className={`inline-flex rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${remaining.className}`}>{remaining.label}</span>}
              </div>

              <dl className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-md bg-gray-50 p-3 text-xs">
                <div><dt className="text-gray-400">지역</dt><dd className="mt-0.5 text-gray-700">{selected.locationName}</dd></div>
                <div><dt className="text-gray-400">고용형태</dt><dd className="mt-0.5 text-gray-700">{selected.jobTypeName}</dd></div>
                <div><dt className="text-gray-400">경력</dt><dd className="mt-0.5 text-gray-700">{selected.experienceYears}</dd></div>
                <div><dt className="text-gray-400">학력</dt><dd className="mt-0.5 text-gray-700">{selected.educationName}</dd></div>
                <div><dt className="text-gray-400">직무</dt><dd className="mt-0.5 text-gray-700">{selected.jobCategoryName}</dd></div>
                <div><dt className="text-gray-400">연봉</dt><dd className="mt-0.5 text-gray-700">{selected.salaryName}</dd></div>
              </dl>

              <div>
                <p className="mb-1 text-xs font-semibold text-gray-700">주요업무</p>
                <ul className="list-inside list-disc space-y-0.5 text-gray-600">
                  {content.responsibilities.map((line) => <li key={line}>{line}</li>)}
                </ul>
              </div>
              <div>
                <p className="mb-1 text-xs font-semibold text-gray-700">자격요건</p>
                <ul className="list-inside list-disc space-y-0.5 text-gray-600">
                  {content.qualifications.map((line) => <li key={line}>{line}</li>)}
                </ul>
              </div>
              <div>
                <p className="mb-1 text-xs font-semibold text-gray-700">우대사항</p>
                <ul className="list-inside list-disc space-y-0.5 text-gray-600">
                  {content.preferred.map((line) => <li key={line}>{line}</li>)}
                </ul>
              </div>
              <div>
                <p className="mb-1 text-xs font-semibold text-gray-700">복지·혜택</p>
                <div className="flex flex-wrap gap-1.5">
                  {content.benefits.map((benefit) => (
                    <span key={benefit} className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600">{benefit}</span>
                  ))}
                </div>
              </div>

              {selected.keywords.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {selected.keywords.map((keyword) => (
                    <span key={keyword} className="rounded-full bg-brand-light px-2 py-0.5 text-xs text-brand-dark">#{keyword}</span>
                  ))}
                </div>
              )}

              <a href={selected.url} target="_blank" rel="noopener noreferrer" className="inline-block text-xs text-brand hover:underline">
                원본 공고 새 탭에서 열기 ↗
              </a>

              <div className="flex items-center gap-2 border-t border-gray-100 pt-3">
                <Button
                  variant={added ? "ghost" : "brand"}
                  disabled={added || addToList.isPending}
                  onClick={() => addToList.mutate(selected)}
                >
                  {added ? "추가됨" : "내 리스트에 추가"}
                </Button>
                {added && (
                  <button type="button" onClick={() => router.push("/app")} className="text-xs text-brand hover:underline">
                    지원현황에서 보기 →
                  </button>
                )}
              </div>
            </div>
          );
        })()}
      </Modal>
    </div>
  );
}
