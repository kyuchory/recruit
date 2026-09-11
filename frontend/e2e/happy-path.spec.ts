import { test, expect, APIRequestContext } from "@playwright/test";

const API_URL = process.env.E2E_API_URL ?? "http://localhost:8080";
const DEV_USER = "00000000-0000-0000-0000-000000000001";

interface EventRow {
  id: string;
  type: string;
}

async function eventsOf(request: APIRequestContext, applicationId: string): Promise<EventRow[]> {
  const res = await request.get(`${API_URL}/api/v1/applications/${applicationId}/events`, {
    headers: { "X-Dev-User-Id": DEV_USER },
  });
  expect(res.ok()).toBeTruthy();
  return res.json();
}

test("dev login -> save URL -> application -> event -> confirm -> rule -> notification", async ({
  page,
  request,
}) => {
  // 1. dev login: the seed user is auto-authenticated; the login page links straight in.
  await page.goto("/login");
  await page.getByRole("link", { name: "바로 시작하기" }).click();
  await expect(page).toHaveURL(/\/app$/);

  // 2. save a job URL
  const jobUrl = `https://jobs.example.com/postings/e2e-${Date.now()}`;
  await page.getByPlaceholder("채용공고 URL 붙여넣기").fill(jobUrl);
  await page.getByRole("button", { name: "저장" }).click();
  await expect(page.getByText("저장했습니다")).toBeVisible();

  // 3. an application row appears (company still "분석 중…" until extraction settles)
  const newRow = page.getByRole("link", { name: "분석 중…" }).first();
  await expect(newRow).toBeVisible();
  await newRow.click();
  await page.waitForURL(/\/app\/applications\/[0-9a-f-]{36}$/);
  const applicationId = page.url().split("/").pop()!;

  // 4. register an event with an exact future date
  const when = new Date(Date.now() + 14 * 864e5).toISOString().slice(0, 16);
  await page.locator('input[type="datetime-local"]').fill(when);
  await page.getByRole("button", { name: "이벤트 추가" }).click();
  await expect(page.getByText("전형 타임라인")).toBeVisible();
  await expect(page.locator("text=UNSCHEDULED").first()).toBeVisible();

  // 5. confirm the schedule
  await page.getByRole("button", { name: "이 일정으로 확인" }).click();
  await expect(page.locator("text=SCHEDULED").first()).toBeVisible();

  // 6. create an IN_APP notification rule (30분 전)
  await page.getByRole("button", { name: "30분 전" }).click();
  await expect(page.getByText("· IN_APP / 30분 전")).toBeVisible();

  // 7. a scheduled notification now exists for the event
  const events = await eventsOf(request, applicationId);
  expect(events.length).toBeGreaterThan(0);
  const eventId = events[0].id;

  await expect
    .poll(async () => {
      const res = await request.get(`${API_URL}/api/v1/events/${eventId}/notifications`, {
        headers: { "X-Dev-User-Id": DEV_USER },
      });
      if (!res.ok()) return [];
      const items: Array<{ deliveryStatus: string }> = await res.json();
      return items.map((i) => i.deliveryStatus);
    })
    .toContain("SCHEDULED");
});
