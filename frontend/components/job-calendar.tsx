"use client";

import { useMemo, useState } from "react";
import type { JobPosting } from "@/lib/job-postings";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function toDateKey(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function buildGrid(year: number, month: number): Date[] {
  const firstOfMonth = new Date(year, month, 1);
  const start = new Date(year, month, 1 - firstOfMonth.getDay());
  return Array.from({ length: 42 }, (_, i) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + i));
}

export function JobCalendar({ jobs, onSelect }: { jobs: JobPosting[]; onSelect: (job: JobPosting) => void }) {
  const [cursor, setCursor] = useState(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  });

  const byDeadline = useMemo(() => {
    const map = new Map<string, JobPosting[]>();
    jobs.forEach((job) => {
      if (!job.deadline) return;
      const key = toDateKey(new Date(job.deadline));
      const list = map.get(key) ?? [];
      list.push(job);
      map.set(key, list);
    });
    return map;
  }, [jobs]);

  const days = useMemo(() => buildGrid(cursor.getFullYear(), cursor.getMonth()), [cursor]);
  const todayKey = toDateKey(new Date());

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <div className="flex items-center justify-center gap-2 border-b border-gray-200 px-4 py-3">
        <button
          type="button"
          onClick={() => setCursor((c) => new Date(c.getFullYear(), c.getMonth() - 1, 1))}
          className="rounded-md px-2 py-1 text-gray-500 hover:bg-gray-100"
          aria-label="이전 달"
        >
          ‹
        </button>
        <span className="text-sm font-semibold">
          {cursor.getFullYear()}. {String(cursor.getMonth() + 1).padStart(2, "0")}
        </span>
        <button
          type="button"
          onClick={() => {
            const now = new Date();
            setCursor(new Date(now.getFullYear(), now.getMonth(), 1));
          }}
          className="rounded-md border border-gray-200 px-2 py-0.5 text-xs text-gray-500 hover:bg-gray-50"
        >
          오늘
        </button>
        <button
          type="button"
          onClick={() => setCursor((c) => new Date(c.getFullYear(), c.getMonth() + 1, 1))}
          className="rounded-md px-2 py-1 text-gray-500 hover:bg-gray-100"
          aria-label="다음 달"
        >
          ›
        </button>
      </div>
      <p className="border-b border-gray-100 px-4 py-2 text-xs text-gray-400">마감일 기준으로 표시합니다.</p>
      <div className="overflow-x-auto">
        <div className="min-w-[700px]">
          <div className="grid grid-cols-7 border-b border-gray-100 bg-gray-50 text-center text-xs font-medium text-gray-500">
            {WEEKDAYS.map((day, index) => (
              <div key={day} className={`py-2 ${index === 0 ? "text-red-500" : index === 6 ? "text-blue-500" : ""}`}>
                {day}
              </div>
            ))}
          </div>
          <div className="grid grid-cols-7">
            {days.map((day) => {
              const key = toDateKey(day);
              const inMonth = day.getMonth() === cursor.getMonth();
              const isToday = key === todayKey;
              const dayJobs = byDeadline.get(key) ?? [];
              const weekday = day.getDay();
              return (
                <div
                  key={key}
                  className={`min-h-[104px] border-b border-r border-gray-100 p-1.5 last:border-r-0 ${inMonth ? "bg-white" : "bg-gray-50"}`}
                >
                  <div
                    className={`text-xs font-medium ${
                      !inMonth
                        ? "text-gray-300"
                        : weekday === 0
                          ? "text-red-500"
                          : weekday === 6
                            ? "text-blue-500"
                            : "text-gray-700"
                    }`}
                  >
                    {isToday ? (
                      <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-brand text-[11px] text-brand-foreground">
                        {day.getDate()}
                      </span>
                    ) : (
                      day.getDate()
                    )}
                  </div>
                  <div className="mt-1 space-y-0.5">
                    {dayJobs.slice(0, 3).map((job) => (
                      <button
                        key={job.id}
                        type="button"
                        onClick={() => onSelect(job)}
                        title={`${job.companyName} · ${job.positionTitle}`}
                        className="block w-full truncate rounded bg-brand-light px-1 py-0.5 text-left text-[11px] leading-tight text-brand-dark hover:opacity-80"
                      >
                        {job.companyName}
                      </button>
                    ))}
                    {dayJobs.length > 3 && (
                      <div className="px-1 text-[10px] text-gray-400">+{dayJobs.length - 3}개 더보기</div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
