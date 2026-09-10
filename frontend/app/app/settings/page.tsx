"use client";

import { useQuery } from "@tanstack/react-query";
import { api, API_BASE_URL } from "@/lib/api";
import { Button } from "@/components/ui";

export default function SettingsPage() {
  const meQ = useQuery({
    queryKey: ["me"],
    queryFn: () => api.get<Record<string, unknown>>("/api/v1/me"),
    retry: false,
  });

  return (
    <div className="max-w-lg space-y-4">
      <h1 className="text-lg font-semibold">설정</h1>

      <section className="rounded-lg border border-gray-200 bg-white p-4 text-sm">
        <h2 className="font-medium">계정</h2>
        {meQ.isError ? (
          <p className="mt-2 text-gray-500">로그인 정보를 불러올 수 없습니다 (개발 환경에서는 시드 사용자).</p>
        ) : (
          <dl className="mt-2 grid grid-cols-2 gap-1 text-gray-600">
            <dt>이메일</dt>
            <dd>{String(meQ.data?.email ?? "-")}</dd>
            <dt>시간대</dt>
            <dd>{String(meQ.data?.timezone ?? "Asia/Seoul")}</dd>
            <dt>이메일 알림</dt>
            <dd>{String(meQ.data?.email_enabled ?? false)}</dd>
          </dl>
        )}
      </section>

      <section className="rounded-lg border border-gray-200 bg-white p-4 text-sm">
        <h2 className="font-medium">알림</h2>
        <p className="mt-1 text-gray-500">
          1차 필수 채널은 이메일과 앱 내 알림입니다. Web Push는 후속 단계입니다.
        </p>
      </section>

      <form action={`${API_BASE_URL}/api/v1/auth/logout`} method="post">
        <Button type="submit" variant="danger">
          로그아웃
        </Button>
      </form>
    </div>
  );
}
