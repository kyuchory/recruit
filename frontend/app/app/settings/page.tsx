"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import { SettingsResponse } from "@/lib/types";
import { Button, Field, inputClass } from "@/components/ui";

const COMMON_ZONES = [
  "Asia/Seoul",
  "Asia/Tokyo",
  "Asia/Shanghai",
  "Asia/Singapore",
  "Australia/Sydney",
  "Europe/London",
  "Europe/Berlin",
  "America/New_York",
  "America/Los_Angeles",
  "UTC",
];

export default function SettingsPage() {
  const router = useRouter();
  const qc = useQueryClient();

  const settingsQ = useQuery({
    queryKey: ["settings"],
    queryFn: () => api.get<SettingsResponse>("/api/v1/settings"),
    retry: false,
  });

  const [draft, setDraft] = useState<{ timezone?: string; emailEnabled?: boolean }>({});
  const [notice, setNotice] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: (body: { expectedVersion: number; timezone?: string; emailEnabled?: boolean }) =>
      api.patch<SettingsResponse>("/api/v1/settings", body),
    onSuccess: (data) => {
      qc.setQueryData(["settings"], data);
      qc.invalidateQueries({ queryKey: ["me"] });
      setDraft({});
      setNotice("저장했습니다.");
    },
    onError: (e: ApiError) => setNotice(e.fields?.timezone ?? e.message),
  });

  const current = settingsQ.data;
  const timezone = draft.timezone ?? current?.timezone ?? "";
  const emailEnabled = draft.emailEnabled ?? current?.emailEnabled ?? false;
  const dirty =
    !!current && (timezone !== current.timezone || emailEnabled !== current.emailEnabled);

  return (
    <div className="max-w-lg space-y-4">
      <h1 className="text-xl font-semibold">설정</h1>

      {settingsQ.isError && (
        <p className="text-sm text-gray-500">설정을 불러올 수 없습니다.</p>
      )}

      {current && (
        <form
          className="space-y-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm"
          onSubmit={(e) => {
            e.preventDefault();
            setNotice(null);
            save.mutate({
              expectedVersion: current.version,
              ...(timezone !== current.timezone ? { timezone } : {}),
              ...(emailEnabled !== current.emailEnabled ? { emailEnabled } : {}),
            });
          }}
        >
          <div className="grid grid-cols-2 gap-1 text-sm text-gray-600">
            <span className="font-medium text-gray-700">이메일</span>
            <span>
              {current.email ?? "-"}
              {current.email && !current.emailVerified && (
                <span className="ml-1 text-amber-600">(미인증)</span>
              )}
            </span>
          </div>

          <Field label="시간대 (일정 표시·알림 계산 기준)">
            <select
              className={inputClass}
              value={timezone}
              onChange={(e) => setDraft((d) => ({ ...d, timezone: e.target.value }))}
            >
              {[...new Set([timezone, ...COMMON_ZONES])].filter(Boolean).map((z) => (
                <option key={z} value={z}>
                  {z}
                </option>
              ))}
            </select>
          </Field>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={emailEnabled}
              onChange={(e) => setDraft((d) => ({ ...d, emailEnabled: e.target.checked }))}
            />
            <span>이메일 알림 받기 (해제하면 미발송 이메일 알림이 즉시 취소됩니다)</span>
          </label>

          <div className="flex items-center gap-3">
            <Button type="submit" variant="brand" disabled={!dirty || save.isPending}>
              저장
            </Button>
            {notice && <span className="text-sm text-gray-500">{notice}</span>}
          </div>
        </form>
      )}

      <section className="rounded-xl border border-gray-200 bg-white p-4 text-sm shadow-sm">
        <h2 className="font-medium">알림 채널</h2>
        <p className="mt-1 text-gray-500">
          1차 필수 채널은 이메일과 앱 내 알림입니다. Web Push는 후속 단계입니다.
        </p>
      </section>

      <Button
        type="button"
        variant="danger"
        onClick={async () => {
          try {
            await api.post("/api/v1/auth/logout");
          } finally {
            router.push("/login");
          }
        }}
      >
        로그아웃
      </Button>
    </div>
  );
}
