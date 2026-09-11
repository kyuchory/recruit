"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import {
  APPLICATION_STATUS_LABELS,
  ApplicationResponse,
  ApplicationStatus,
  EVENT_RESULT_LABELS,
  EVENT_STATUS_LABELS,
  EventResponse,
  EVENT_LABELS,
  EventType,
  EXTRACTION_STATUS_LABELS,
  ExtractionRunResponse,
  InboxItem,
  NOTIFICATION_CHANNEL_LABELS,
  NOTIFICATION_STATUS_LABELS,
  RuleResponse,
} from "@/lib/types";
import { Badge, Button, Field, inputClass } from "@/components/ui";

const EVENT_TYPES: EventType[] = [
  "DOCUMENT_DEADLINE", "NCS", "CODING_TEST", "AI_ASSESSMENT",
  "INTERVIEW_1", "INTERVIEW_2", "FINAL_INTERVIEW", "RESULT_ANNOUNCEMENT", "ORIENTATION", "CUSTOM",
];

export default function ApplicationDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const qc = useQueryClient();
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["application", id] });
    qc.invalidateQueries({ queryKey: ["events", id] });
    // application-level status/archive changes can cancel notifications server-side
    // without changing the event itself, so refresh every event's notification list too.
    qc.invalidateQueries({ predicate: (q) => q.queryKey[0] === "event-notifications" });
  };

  const appQ = useQuery({
    queryKey: ["application", id],
    queryFn: () => api.get<ApplicationResponse>(`/api/v1/applications/${id}`),
  });
  const eventsQ = useQuery({
    queryKey: ["events", id],
    queryFn: () => api.get<EventResponse[]>(`/api/v1/applications/${id}/events`),
  });
  const linkQ = useQuery({
    queryKey: ["link", appQ.data?.linkId],
    enabled: !!appQ.data?.linkId,
    queryFn: () => api.get<{ latestRun: ExtractionRunResponse | null; originalUrl: string | null }>(
      `/api/v1/links/${appQ.data!.linkId}`,
    ),
    refetchInterval: (q) => {
      const s = q.state.data?.latestRun?.status;
      return s === "QUEUED" || s === "RUNNING" ? 2500 : false;
    },
  });

  const app = appQ.data;
  const run = linkQ.data?.latestRun;

  if (appQ.isLoading) return <p className="text-sm text-gray-400">불러오는 중…</p>;
  if (!app) return <p className="text-sm text-red-600">지원 건을 찾을 수 없습니다.</p>;

  return (
    <div className="space-y-6">
      <Link href="/app" className="text-sm text-blue-600 hover:underline">
        ← 지원현황
      </Link>

      <ApplicationHeader app={app} onSaved={invalidate} />

      {run && (
        <div className="rounded-lg border border-gray-200 bg-white p-4 text-sm">
          <div className="flex items-center justify-between">
            <span className="font-medium">분석 상태</span>
            <Badge tone={run.status === "SUCCEEDED" ? "green" : run.status === "FAILED" ? "red" : "amber"}>
              {EXTRACTION_STATUS_LABELS[run.status]}
            </Badge>
          </div>
          {run.status === "SUCCEEDED" && app.reviewStatus === "PENDING" && (
            <ConfirmPanel appId={id} version={app.version} result={run.result} runId={run.id} onDone={invalidate} />
          )}
          {(run.status === "NEEDS_INPUT" || run.status === "FAILED") && (
            <p className="mt-2 text-gray-500">
              자동 분석이 어려웠습니다. 아래에서 회사·직무·일정을 직접 입력하세요.
            </p>
          )}
          {Array.isArray(run.warnings) && run.warnings.length > 0 && (
            <p className="mt-2 text-xs text-gray-400">warnings: {run.warnings.join(", ")}</p>
          )}
        </div>
      )}

      <section>
        <h2 className="mb-2 text-sm font-semibold text-gray-700">전형 타임라인</h2>
        <div className="space-y-3">
          {(eventsQ.data ?? []).map((e) => (
            <EventCard key={e.id} event={e} onChanged={invalidate} />
          ))}
          {(eventsQ.data ?? []).length === 0 && (
            <p className="text-sm text-gray-400">아직 등록된 전형이 없습니다.</p>
          )}
        </div>
        <AddEvent appId={id} onAdded={invalidate} />
      </section>
    </div>
  );
}

function ApplicationHeader({ app, onSaved }: { app: ApplicationResponse; onSaved: () => void }) {
  const [company, setCompany] = useState(app.companyName ?? "");
  const [position, setPosition] = useState(app.positionTitle ?? "");
  const [status, setStatus] = useState(app.status);
  const [notice, setNotice] = useState<{ text: string; tone: "ok" | "error" } | null>(null);
  const save = useMutation({
    mutationFn: () =>
      api.patch<ApplicationResponse>(`/api/v1/applications/${app.id}`, {
        expectedVersion: app.version,
        companyName: company,
        positionTitle: position,
        status,
      }),
    onSuccess: () => {
      onSaved();
      setNotice({ text: "저장했습니다.", tone: "ok" });
    },
    onError: (e: Error) => setNotice({ text: e.message, tone: "error" }),
  });
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="회사명">
          <input value={company} onChange={(e) => setCompany(e.target.value)} className={inputClass} />
        </Field>
        <Field label="직무">
          <input value={position} onChange={(e) => setPosition(e.target.value)} className={inputClass} />
        </Field>
        <Field label="지원 상태">
          <select value={status} onChange={(e) => setStatus(e.target.value as typeof status)} className={inputClass}>
            {(
              ["INTERESTED", "PLANNED", "APPLIED", "IN_PROGRESS", "ACCEPTED", "REJECTED", "WITHDRAWN"] as ApplicationStatus[]
            ).map((s) => (
              <option key={s} value={s}>
                {APPLICATION_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="mt-3 flex items-center gap-2">
        <Button
          onClick={() => {
            setNotice(null);
            save.mutate();
          }}
          disabled={save.isPending}
        >
          저장
        </Button>
        {notice && (
          <span className={"text-xs " + (notice.tone === "ok" ? "text-green-600" : "text-red-600")}>
            {notice.text}
          </span>
        )}
      </div>
    </div>
  );
}

function ConfirmPanel({
  appId,
  version,
  result,
  runId,
  onDone,
}: {
  appId: string;
  version: number;
  result: Record<string, unknown>;
  runId: string;
  onDone: () => void;
}) {
  const company =
    ((result.companyName as { value?: string })?.value ??
      (result.aiFields as { companyName?: string })?.companyName) ||
    "";
  const positions = (result.positions as Array<{ title?: { value?: string } }>) ?? [];
  const position = positions[0]?.title?.value ?? "";
  const [c, setC] = useState(company);
  const [p, setP] = useState(position);
  const confirm = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/applications/${appId}/confirm`, {
        expectedVersion: version,
        runId,
        companyName: c || undefined,
        positionTitle: p || undefined,
      }),
    onSuccess: onDone,
  });
  return (
    <div className="mt-3 rounded-md bg-gray-50 p-3">
      <p className="text-xs text-gray-500">추출 제안을 검토하고 확정하세요. 직접 수정한 값은 덮어쓰지 않습니다.</p>
      <div className="mt-2 grid gap-2 sm:grid-cols-2">
        <input value={c} onChange={(e) => setC(e.target.value)} className={inputClass} placeholder="회사명" />
        <input value={p} onChange={(e) => setP(e.target.value)} className={inputClass} placeholder="직무" />
      </div>
      <Button className="mt-2" onClick={() => confirm.mutate()} disabled={confirm.isPending}>
        확정
      </Button>
    </div>
  );
}

function AddEvent({ appId, onAdded }: { appId: string; onAdded: () => void }) {
  const [type, setType] = useState<EventType>("CODING_TEST");
  const [label, setLabel] = useState("");
  const [startAt, setStartAt] = useState("");
  const add = useMutation({
    mutationFn: () => {
      const body: Record<string, unknown> = { type, scheduleKind: startAt ? "EXACT" : "UNKNOWN" };
      if (type === "CUSTOM") body.customLabel = label;
      if (startAt) {
        const iso = new Date(startAt).toISOString();
        body.startAt = iso;
        body.scheduledAt = iso;
      }
      return api.post(`/api/v1/applications/${appId}/events`, body);
    },
    onSuccess: () => {
      setStartAt("");
      setLabel("");
      onAdded();
    },
  });
  return (
    <div className="mt-4 rounded-lg border border-dashed border-gray-300 p-3">
      <div className="flex flex-wrap items-end gap-2">
        <Field label="전형">
          <select value={type} onChange={(e) => setType(e.target.value as EventType)} className={inputClass}>
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {EVENT_LABELS[t]}
              </option>
            ))}
          </select>
        </Field>
        {type === "CUSTOM" && (
          <Field label="이름">
            <input value={label} onChange={(e) => setLabel(e.target.value)} className={inputClass} />
          </Field>
        )}
        <Field label="일시 (선택)">
          <input
            type="datetime-local"
            value={startAt}
            onChange={(e) => setStartAt(e.target.value)}
            className={inputClass}
          />
        </Field>
        <Button onClick={() => add.mutate()} disabled={add.isPending}>
          이벤트 추가
        </Button>
      </div>
      {add.isError && <p className="mt-1 text-xs text-red-600">{(add.error as Error).message}</p>}
    </div>
  );
}

function EventCard({ event, onChanged }: { event: EventResponse; onChanged: () => void }) {
  const qc = useQueryClient();
  const rulesQ = useQuery({
    queryKey: ["rules", event.id],
    queryFn: () => api.get<RuleResponse[]>(`/api/v1/events/${event.id}/notification-rules`),
  });
  // The rule list above is the standing configuration; this is the actual materialized
  // notifications for this event, so cancellation (status change, archive, ...) shows up here.
  const notificationsQ = useQuery({
    queryKey: ["event-notifications", event.id],
    queryFn: () => api.get<InboxItem[]>(`/api/v1/events/${event.id}/notifications`),
  });

  const confirm = useMutation({
    mutationFn: () => api.post(`/api/v1/events/${event.id}/confirm`, { expectedVersion: event.version }),
    onSuccess: () => {
      onChanged();
      qc.invalidateQueries({ queryKey: ["event-notifications", event.id] });
    },
  });
  const addRule = useMutation({
    mutationFn: (offsetMinutes: number) =>
      api.post(`/api/v1/events/${event.id}/notification-rules`, {
        channel: "IN_APP",
        anchor: event.scheduleKind === "DATE_ONLY" ? "DATE" : "START_AT",
        mode: event.scheduleKind === "DATE_ONLY" ? "CALENDAR_DAYS" : "BEFORE_MINUTES",
        offsetMinutes: event.scheduleKind === "DATE_ONLY" ? undefined : offsetMinutes,
        offsetDays: event.scheduleKind === "DATE_ONLY" ? 1 : undefined,
        localTime: event.scheduleKind === "DATE_ONLY" ? "09:00:00" : undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rules", event.id] });
      qc.invalidateQueries({ queryKey: ["event-notifications", event.id] });
    },
  });

  const label = event.customLabel ?? EVENT_LABELS[event.type];
  const when =
    event.scheduledAt ?? event.startAt
      ? new Date((event.scheduledAt ?? event.startAt)!).toLocaleString("ko-KR")
      : event.scheduledDate
        ? `${event.scheduledDate} (시간 미정)`
        : "날짜 미정";

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-3 text-sm">
      <div className="flex items-center justify-between">
        <div>
          <span className="font-medium">{label}</span>
          <span className="ml-2 text-gray-500">{when}</span>
        </div>
        <div className="flex gap-1">
          <Badge tone={event.status === "SCHEDULED" ? "blue" : "gray"}>{EVENT_STATUS_LABELS[event.status]}</Badge>
          <Badge tone={event.result === "PASSED" ? "green" : event.result === "FAILED" ? "red" : "gray"}>
            {EVENT_RESULT_LABELS[event.result]}
          </Badge>
        </div>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        {(event.scheduleKind === "EXACT" || event.scheduleKind === "DATE_ONLY") && !event.confirmedAt && (
          <Button variant="ghost" onClick={() => confirm.mutate()} disabled={confirm.isPending}>
            이 일정으로 확인
          </Button>
        )}
        {event.confirmedAt && (
          <>
            <span className="text-xs text-gray-500">알림:</span>
            {event.scheduleKind === "DATE_ONLY" ? (
              <Button variant="ghost" onClick={() => addRule.mutate(0)}>
                하루 전 09:00
              </Button>
            ) : (
              <>
                <Button variant="ghost" onClick={() => addRule.mutate(1440)}>
                  하루 전
                </Button>
                <Button variant="ghost" onClick={() => addRule.mutate(180)}>
                  3시간 전
                </Button>
                <Button variant="ghost" onClick={() => addRule.mutate(30)}>
                  30분 전
                </Button>
              </>
            )}
          </>
        )}
      </div>
      {(rulesQ.data ?? []).length > 0 && (
        <ul className="mt-2 text-xs text-gray-500">
          {rulesQ.data!.map((r) => (
            <li key={r.id}>
              · {NOTIFICATION_CHANNEL_LABELS[r.channel]} /{" "}
              {r.mode === "BEFORE_MINUTES" ? `${r.offsetMinutes}분 전` : `${r.offsetDays}일 전 ${r.localTime}`}
            </li>
          ))}
        </ul>
      )}
      {(notificationsQ.data ?? []).length > 0 && (
        <div className="mt-2 border-t border-gray-100 pt-2">
          <span className="text-xs text-gray-400">예약된 알림</span>
          <ul className="mt-1 text-xs text-gray-500">
            {notificationsQ.data!.map((n) => (
              <li key={n.id} className="flex items-center gap-1.5">
                · {new Date(n.scheduledSendAt).toLocaleString("ko-KR")}
                <Badge tone={n.deliveryStatus === "CANCELLED" || n.deliveryStatus === "FAILED" ? "gray" : "blue"}>
                  {NOTIFICATION_STATUS_LABELS[n.deliveryStatus]}
                </Badge>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
