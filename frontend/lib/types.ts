export type ApplicationStatus =
  | "INTERESTED" | "PLANNED" | "APPLIED" | "IN_PROGRESS" | "ACCEPTED" | "REJECTED" | "WITHDRAWN";

export type EventType =
  | "DOCUMENT_DEADLINE" | "NCS" | "CODING_TEST" | "AI_ASSESSMENT"
  | "INTERVIEW_1" | "INTERVIEW_2" | "FINAL_INTERVIEW"
  | "RESULT_ANNOUNCEMENT" | "ORIENTATION" | "CUSTOM";

export type ScheduleKind = "EXACT" | "DATE_ONLY" | "UNKNOWN" | "ROLLING" | "UNTIL_FILLED";
export type EventStatus = "UNSCHEDULED" | "SCHEDULED" | "COMPLETED" | "CANCELLED";
export type EventResult = "NOT_STARTED" | "WAITING" | "PASSED" | "FAILED" | "SKIPPED";
export type ReviewStatus = "PENDING" | "CONFIRMED" | "NOT_REQUIRED";
export type NotificationChannel = "EMAIL" | "IN_APP" | "WEB_PUSH" | "FCM" | "APNS";
/** Friendly names the inbox API returns for Notification.status (see NotificationInboxController). */
export type NotificationDeliveryStatus = "SCHEDULED" | "PROCESSING" | "SENT" | "PARTIAL" | "FAILED" | "CANCELLED";

export interface ApplicationResponse {
  id: string;
  linkId: string;
  positionKey: string;
  companyName: string | null;
  positionTitle: string | null;
  location: string | null;
  status: ApplicationStatus;
  appliedAt: string | null;
  notes: string;
  reviewStatus: ReviewStatus;
  archivedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EventResponse {
  id: string;
  applicationId: string;
  type: EventType;
  customLabel: string | null;
  sortOrder: number;
  scheduleKind: ScheduleKind;
  scheduledAt: string | null;
  startAt: string | null;
  endAt: string | null;
  scheduledDate: string | null;
  timezone: string;
  location: string | null;
  url: string | null;
  notes: string;
  status: EventStatus;
  result: EventResult;
  confirmedAt: string | null;
  scheduleVersion: number;
  version: number;
}

export interface LinkImportResponse {
  linkId: string;
  applicationId: string;
  extractionRunId: string | null;
  status: string;
  duplicate: boolean;
}

export type ExtractionRunStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "NEEDS_INPUT" | "FAILED" | "CANCELLED";

export interface ExtractionRunResponse {
  id: string;
  status: ExtractionRunStatus;
  progressStage: string | null;
  attemptCount: number;
  result: Record<string, unknown>;
  warnings: unknown[];
  errorCode: string | null;
}

export interface RuleResponse {
  id: string;
  eventId: string;
  channel: NotificationChannel;
  anchor: "SCHEDULED_AT" | "START_AT" | "END_AT" | "DATE";
  mode: "BEFORE_MINUTES" | "CALENDAR_DAYS";
  offsetMinutes: number | null;
  offsetDays: number | null;
  localTime: string | null;
  enabled: boolean;
  version: number;
}

export interface InboxItem {
  id: string;
  eventId: string;
  scheduledSendAt: string;
  visibleAt: string | null;
  readAt: string | null;
  deliveryStatus: NotificationDeliveryStatus;
  payload: Record<string, unknown>;
}

export interface InboxPage {
  items: InboxItem[];
  unreadCount: number;
  page: number;
  size: number;
  totalElements: number;
}

export interface SummaryEventItem {
  eventId: string;
  applicationId: string;
  companyName: string | null;
  type: EventType;
  label: string | null;
  scheduleKind: ScheduleKind;
  scheduledAt: string | null;
  scheduledDate: string | null;
}

export interface SummaryReviewItem {
  applicationId: string;
  companyName: string | null;
  positionTitle: string | null;
}

export interface SummaryResponse {
  timezone: string;
  todayCount: number;
  today: SummaryEventItem[];
  thisWeekCount: number;
  thisWeek: SummaryEventItem[];
  needsReviewCount: number;
  needsReview: SummaryReviewItem[];
}

export interface SettingsResponse {
  email: string | null;
  emailVerified: boolean;
  emailEnabled: boolean;
  timezone: string;
  version: number;
}

export const EVENT_LABELS: Record<EventType, string> = {
  DOCUMENT_DEADLINE: "서류",
  NCS: "NCS",
  CODING_TEST: "코테",
  AI_ASSESSMENT: "AI 역량검사",
  INTERVIEW_1: "1차",
  INTERVIEW_2: "2차",
  FINAL_INTERVIEW: "최종",
  RESULT_ANNOUNCEMENT: "발표",
  ORIENTATION: "OT",
  CUSTOM: "기타",
};

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  INTERESTED: "관심",
  PLANNED: "지원 예정",
  APPLIED: "지원 완료",
  IN_PROGRESS: "전형 진행중",
  ACCEPTED: "합격",
  REJECTED: "불합격",
  WITHDRAWN: "지원 철회",
};

export const EVENT_STATUS_LABELS: Record<EventStatus, string> = {
  UNSCHEDULED: "일정 미확정",
  SCHEDULED: "일정 확정",
  COMPLETED: "완료",
  CANCELLED: "취소됨",
};

export const EVENT_RESULT_LABELS: Record<EventResult, string> = {
  NOT_STARTED: "결과 대기",
  WAITING: "발표 대기",
  PASSED: "합격",
  FAILED: "불합격",
  SKIPPED: "건너뜀",
};

export const REVIEW_STATUS_LABELS: Record<ReviewStatus, string> = {
  PENDING: "확인 필요",
  CONFIRMED: "확인 완료",
  NOT_REQUIRED: "확인 불필요",
};

export const NOTIFICATION_CHANNEL_LABELS: Record<NotificationChannel, string> = {
  EMAIL: "이메일",
  IN_APP: "앱 알림",
  WEB_PUSH: "웹 푸시",
  FCM: "FCM",
  APNS: "APNS",
};

export const EXTRACTION_STATUS_LABELS: Record<ExtractionRunStatus, string> = {
  QUEUED: "대기중",
  RUNNING: "분석중",
  SUCCEEDED: "분석 완료",
  NEEDS_INPUT: "직접 입력 필요",
  FAILED: "분석 실패",
  CANCELLED: "취소됨",
};

export const NOTIFICATION_STATUS_LABELS: Record<NotificationDeliveryStatus, string> = {
  SCHEDULED: "예약됨",
  PROCESSING: "발송 처리중",
  SENT: "발송됨",
  PARTIAL: "일부 발송",
  FAILED: "발송 실패",
  CANCELLED: "취소됨",
};
