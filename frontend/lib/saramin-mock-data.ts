/**
 * Mock data shaped exactly like the Saramin Open API `job-search` response
 * (see https://oapi.saramin.co.kr/guide/job-search). Kept separate from
 * `job-postings.ts`'s normalized shape so swapping this file for a real API
 * call later doesn't touch any UI code — only `normalizeSaraminJob` does.
 */

export type SaraminJob = {
  id: string;
  url: string;
  active: 0 | 1;
  "posting-timestamp": number;
  "expiration-timestamp": number | null;
  "close-type": { code: 1 | 2 | 3 | 4; name: string };
  company: { name: string; "company-type": string };
  position: {
    title: string;
    location: { code: string; name: string };
    "job-type": { code: number; name: string };
    industry: { code: number; name: string };
    "job-code": { code: number; name: string };
  };
  "experience-level": { code: number; min: number; max: number; name: string };
  "required-education-level": { code: number; name: string };
  salary: { code: number; name: string };
  keyword: string;
};

const COMPANIES: { name: string; type: string }[] = [
  { name: "삼성전자", type: "대기업" },
  { name: "LG전자", type: "대기업" },
  { name: "SK하이닉스", type: "대기업" },
  { name: "현대자동차", type: "대기업" },
  { name: "기아", type: "대기업" },
  { name: "포스코", type: "대기업" },
  { name: "네이버", type: "대기업" },
  { name: "카카오", type: "대기업" },
  { name: "쿠팡", type: "대기업" },
  { name: "한화시스템", type: "대기업" },
  { name: "신한은행", type: "대기업" },
  { name: "국민은행", type: "대기업" },
  { name: "한국전력공사", type: "공공기관" },
  { name: "한국가스공사(KOGAS)", type: "공공기관" },
  { name: "한국수력원자력", type: "공공기관" },
  { name: "한국철도공사", type: "공공기관" },
  { name: "국민건강보험공단", type: "공공기관" },
  { name: "한국관광공사", type: "공공기관" },
  { name: "토스", type: "중견기업" },
  { name: "당근마켓", type: "중견기업" },
  { name: "야놀자", type: "중견기업" },
  { name: "무신사", type: "중견기업" },
  { name: "컬리", type: "중견기업" },
  { name: "왓챠", type: "중견기업" },
  { name: "리디", type: "중견기업" },
  { name: "직방", type: "중견기업" },
  { name: "여기어때", type: "중견기업" },
  { name: "우아한형제들", type: "중견기업" },
  { name: "크래프톤", type: "중견기업" },
  { name: "펄어비스", type: "중견기업" },
  { name: "넥슨", type: "중견기업" },
  { name: "넷마블", type: "중견기업" },
  { name: "센드버드", type: "기타기업" },
  { name: "몰로코", type: "기타기업" },
  { name: "리멤버", type: "기타기업" },
  { name: "잡플래닛", type: "기타기업" },
  { name: "원티드랩", type: "기타기업" },
  { name: "베스핀글로벌", type: "기타기업" },
  { name: "메가존클라우드", type: "기타기업" },
  { name: "쏘카", type: "기타기업" },
];

const JOB_CODES: { code: number; name: string }[] = [
  { code: 1, name: "경영·사무" },
  { code: 2, name: "회계·세무·재무" },
  { code: 3, name: "마케팅·광고·MD" },
  { code: 4, name: "IT개발·데이터" },
  { code: 5, name: "디자인" },
  { code: 6, name: "영업·판매·무역" },
  { code: 7, name: "유통·물류" },
  { code: 8, name: "생산·제조" },
  { code: 9, name: "고객상담·TM" },
  { code: 10, name: "연구·R&D" },
];

const INDUSTRIES: { code: number; name: string }[] = [
  { code: 1, name: "서비스업" },
  { code: 2, name: "제조·화학" },
  { code: 3, name: "IT·웹·통신" },
  { code: 4, name: "은행·금융업" },
  { code: 5, name: "미디어·엔터테인먼트" },
  { code: 6, name: "유통·무역" },
  { code: 7, name: "건설업" },
  { code: 8, name: "의료·제약·복지" },
];

const LOCATIONS: { code: string; name: string }[] = [
  { code: "101000", name: "서울 전체" },
  { code: "101010", name: "서울 강남구" },
  { code: "101020", name: "서울 마포구" },
  { code: "101030", name: "서울 영등포구" },
  { code: "102000", name: "경기 전체" },
  { code: "102010", name: "경기 성남시 분당구" },
  { code: "102020", name: "경기 수원시" },
  { code: "106000", name: "부산 전체" },
  { code: "108000", name: "대구 전체" },
  { code: "104000", name: "인천 전체" },
  { code: "109000", name: "대전 전체" },
];

// job-type code follows Saramin's code table (1 정규직, 2 계약직, 4 인턴직, ...);
// experience-level 0 means 신입.
const HIRE_TYPES: { jobType: { code: number; name: string }; experience: { code: number; min: number; max: number; name: string } }[] = [
  { jobType: { code: 1, name: "정규직" }, experience: { code: 0, min: 0, max: 0, name: "신입" } },
  { jobType: { code: 1, name: "정규직" }, experience: { code: 1, min: 1, max: 5, name: "경력 1~5년" } },
  { jobType: { code: 4, name: "인턴직" }, experience: { code: 0, min: 0, max: 0, name: "인턴" } },
  { jobType: { code: 2, name: "계약직" }, experience: { code: 1, min: 0, max: 3, name: "경력무관" } },
  { jobType: { code: 9, name: "교육" }, experience: { code: 0, min: 0, max: 0, name: "교육생" } },
];

const EDUCATION_LEVELS: { code: number; name: string }[] = [
  { code: 0, name: "학력무관" },
  { code: 2, name: "고졸이상" },
  { code: 3, name: "대학교졸업(4년)" },
  { code: 8, name: "석사졸업" },
];

const SALARIES: { code: number; name: string }[] = [
  { code: 99, name: "면접 후 결정" },
  { code: 11, name: "3,000만원 이상" },
  { code: 13, name: "3,500만원 이상" },
  { code: 15, name: "4,000만원 이상" },
  { code: 18, name: "5,000만원 이상" },
];

const POSITION_TITLES = [
  "백엔드 개발자", "프론트엔드 개발자", "iOS 개발자", "안드로이드 개발자",
  "데이터 엔지니어", "데이터 분석가", "머신러닝 엔지니어", "DevOps 엔지니어",
  "QA 엔지니어", "프로덕트 매니저", "서비스 기획자", "UX 디자이너", "UI 디자이너",
  "그로스 마케터", "퍼포먼스 마케터", "영업 관리", "인사 담당자", "재무 담당자",
  "물류 관리자", "생산 관리 엔지니어", "고객상담 매니저", "연구원",
];

const KEYWORD_POOL = [
  "재택근무", "자율출퇴근", "스톡옵션", "신입환영", "수시채용", "야근없음",
  "해외출장", "복지좋은", "성장가능", "테크블로그", "사내스터디",
];

function pick<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}
function randInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}
function unixDaysFromNow(days: number): number {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return Math.floor(d.getTime() / 1000);
}

function buildJob(index: number): SaraminJob {
  const company = pick(COMPANIES);
  const hire = pick(HIRE_TYPES);
  const jobCode = pick(JOB_CODES);
  const industry = pick(INDUSTRIES);
  const location = pick(LOCATIONS);
  const education = pick(EDUCATION_LEVELS);
  const salary = pick(SALARIES);
  const title = pick(POSITION_TITLES);

  const postedDaysAgo = randInt(0, 20);
  const closeTypeCode = pick([1, 1, 1, 2, 3] as const); // mostly fixed-deadline
  const closeTypeName = closeTypeCode === 1 ? "마감일 지정" : closeTypeCode === 2 ? "채용시 마감" : closeTypeCode === 3 ? "상시채용" : "수시채용";
  const expiration = closeTypeCode === 1 ? unixDaysFromNow(randInt(-5, 45)) : null;

  const keywordCount = randInt(1, 3);
  const keywords = Array.from({ length: keywordCount }, () => pick(KEYWORD_POOL));

  return {
    id: String(30000000 + index),
    url: `https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=${30000000 + index}`,
    active: expiration !== null && expiration < unixDaysFromNow(0) ? 0 : 1,
    "posting-timestamp": unixDaysFromNow(-postedDaysAgo),
    "expiration-timestamp": expiration,
    "close-type": { code: closeTypeCode, name: closeTypeName },
    company: { name: company.name, "company-type": company.type },
    position: {
      title: `${title} (${index + 1})`,
      location,
      "job-type": hire.jobType,
      industry,
      "job-code": jobCode,
    },
    "experience-level": hire.experience,
    "required-education-level": education,
    salary,
    keyword: [...new Set(keywords)].join(","),
  };
}

export const MOCK_SARAMIN_JOBS: SaraminJob[] = Array.from({ length: 120 }, (_, i) => buildJob(i));
