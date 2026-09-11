"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { EVENT_LABELS, EventType, InboxPage, NOTIFICATION_STATUS_LABELS } from "@/lib/types";
import { Badge, Button } from "@/components/ui";

export default function NotificationsPage() {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["notifications"],
    queryFn: () => api.get<InboxPage>("/api/v1/notifications?size=50"),
    refetchInterval: 20_000,
  });
  const markRead = useMutation({
    mutationFn: (id: string) => api.patch(`/api/v1/notifications/${id}`, { read: true }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });

  const items = q.data?.items ?? [];

  return (
    <div>
      <h1 className="text-lg font-semibold">
        알림함 {q.data ? <span className="text-sm text-gray-400">읽지 않음 {q.data.unreadCount}</span> : null}
      </h1>
      <p className="mt-1 text-xs text-gray-400">
        앱 내 알림입니다. 발송 요청 완료는 실제 수신을 의미하지 않습니다.
      </p>

      <ul className="mt-4 space-y-2">
        {items.length === 0 && <li className="text-sm text-gray-400">알림이 없습니다.</li>}
        {items.map((n) => {
          const p = n.payload as { customLabel?: string; eventType?: string; path?: string };
          const eventLabel = p.customLabel ?? (p.eventType ? EVENT_LABELS[p.eventType as EventType] : null) ?? "채용 일정";
          return (
            <li
              key={n.id}
              className={
                "rounded-lg border p-3 text-sm " +
                (n.readAt ? "border-gray-200 bg-white" : "border-blue-200 bg-blue-50")
              }
            >
              <div className="flex items-center justify-between">
                <span className="font-medium">{eventLabel} 알림</span>
                <Badge tone="gray">{NOTIFICATION_STATUS_LABELS[n.deliveryStatus]}</Badge>
              </div>
              <div className="mt-1 text-xs text-gray-500">
                예정 {new Date(n.scheduledSendAt).toLocaleString("ko-KR")}
              </div>
              {!n.readAt && (
                <Button variant="ghost" className="mt-2" onClick={() => markRead.mutate(n.id)}>
                  읽음
                </Button>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
