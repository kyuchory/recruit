export type ApplicationStatus =
  | "INTERESTED" | "PLANNED" | "APPLIED" | "IN_PROGRESS" | "ACCEPTED" | "REJECTED" | "WITHDRAWN";

export type EventType =
  | "DOCUMENT_DEADLINE" | "NCS" | "CODING_TEST" | "AI_ASSESSMENT"
  | "INTERVIEW_1" | "INTERVIEW_2" | "FINAL_INTERVIEW"
  | "RESULT_ANNOUNCEMENT" | "ORIENTATION" | "CUSTOM";

export type ScheduleKind = "EXACT" | "DATE_ONLY" | "UNKNOWN" | "ROLLING" | "UNTIL_FILLED";
export type EventStatus = "UNSCHEDULED" | "SCHEDULED" | "COMPLETED" | "CANCELLED";
export type EventResult = "NOT_STARTED" | "WAITING" | "PASSED" | "FAILED" | "SKIPPED";

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
  reviewStatus: "PENDING" | "CONFIRMED" | "NOT_REQUIRED";
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

export interface ExtractionRunResponse {
  id: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "NEEDS_INPUT" | "FAILED" | "CANCELLED";
  progressStage: string | null;
  attemptCount: number;
  result: Record<string, unknown>;
  warnings: unknown[];
  errorCode: string | null;
}

export interface RuleResponse {
  id: string;
  eventId: string;
  channel: "EMAIL" | "IN_APP" | "WEB_PUSH" | "FCM" | "APNS";
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
  deliveryStatus: string;
  payload: Record<string, unknown>;
}

export interface InboxPage {
  items: InboxItem[];
  unreadCount: number;
  page: number;
  size: number;
  totalElements: number;
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
