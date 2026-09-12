import { MOCK_SARAMIN_JOBS, type SaraminJob } from "@/lib/saramin-mock-data";

/**
 * UI-friendly shape normalized from the raw Saramin API job object. This is
 * the only place that needs to change when `saramin-mock-data.ts` is swapped
 * for a real `/job-search` call — every component below reads `JobPosting`.
 */
export type JobPosting = {
  id: string;
  url: string;
  isActive: boolean;
  companyName: string;
  companyType: string;
  positionTitle: string;
  locationName: string;
  jobTypeName: string;
  hireTypeName: string;
  experienceYears: string;
  jobCategoryName: string;
  industryName: string;
  educationName: string;
  salaryName: string;
  postedAt: string;
  deadline: string | null;
  closeTypeName: string;
  keywords: string[];
};

function hireTypeFrom(job: SaraminJob): string {
  const exp = job["experience-level"].name;
  if (exp.includes("신입")) return "신입";
  if (exp.includes("인턴")) return "인턴";
  if (exp.includes("교육")) return "교육";
  if (job.position["job-type"].name === "계약직") return "계약직";
  return "경력";
}

function experienceYearsFrom(job: SaraminJob): string {
  const { min, max, name } = job["experience-level"];
  if (name.includes("신입")) return "신입";
  if (name.includes("인턴")) return "인턴";
  if (name.includes("교육")) return "교육생 채용";
  if (min === 0 && max === 0) return "경력무관";
  return `경력 ${min}~${max}년`;
}

export function normalizeSaraminJob(job: SaraminJob): JobPosting {
  return {
    id: job.id,
    url: job.url,
    isActive: job.active === 1,
    companyName: job.company.name,
    companyType: job.company["company-type"],
    positionTitle: job.position.title,
    locationName: job.position.location.name,
    jobTypeName: job.position["job-type"].name,
    hireTypeName: hireTypeFrom(job),
    experienceYears: experienceYearsFrom(job),
    jobCategoryName: job.position["job-code"].name,
    industryName: job.position.industry.name,
    educationName: job["required-education-level"].name,
    salaryName: job.salary.name,
    postedAt: new Date(job["posting-timestamp"] * 1000).toISOString(),
    deadline: job["expiration-timestamp"] !== null ? new Date(job["expiration-timestamp"] * 1000).toISOString() : null,
    closeTypeName: job["close-type"].name,
    keywords: job.keyword ? job.keyword.split(",").filter(Boolean) : [],
  };
}

export const JOB_POSTINGS: JobPosting[] = MOCK_SARAMIN_JOBS.map(normalizeSaraminJob);

export const HIRE_TYPES = ["신입", "경력", "인턴", "계약직", "교육"] as const;
export const COMPANY_TYPES = ["대기업", "중견기업", "공공기관", "기타기업"] as const;

export const JOB_CATEGORIES = [...new Set(JOB_POSTINGS.map((j) => j.jobCategoryName))].sort();
export const LOCATIONS = [...new Set(JOB_POSTINGS.map((j) => j.locationName))].sort();

export function daysUntil(deadline: string | null): number | null {
  if (!deadline) return null;
  const diff = new Date(deadline).setHours(0, 0, 0, 0) - new Date().setHours(0, 0, 0, 0);
  return Math.round(diff / 86_400_000);
}
