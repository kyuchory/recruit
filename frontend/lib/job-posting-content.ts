import type { JobPosting } from "@/lib/job-postings";

/**
 * The real Saramin `/job-search` response does NOT include a job description,
 * requirements, or view/scrap counts — those only exist on the actual posting
 * page. This module fabricates plausible template content per job category so
 * the detail view isn't just five label/value pairs. When a real integration
 * needs this content (e.g. by scraping the posting page or an AI summary of
 * it), replace `getJobContent` — nothing else in the UI needs to change.
 */

type CategoryContent = {
  responsibilities: string[];
  qualifications: string[];
  preferred: string[];
  benefits: string[];
};

const CONTENT_BY_CATEGORY: Record<string, CategoryContent> = {
  "IT개발·데이터": {
    responsibilities: ["서비스 백엔드/프론트엔드 기능 설계 및 개발", "서비스 안정성을 위한 모니터링·장애 대응", "팀 내 코드 리뷰 및 기술 문서화"],
    qualifications: ["관련 분야 실무 경험 또는 이에 준하는 프로젝트 경험", "협업 도구(Git 등) 사용 경험", "문제 해결을 위한 논리적 사고력"],
    preferred: ["대용량 트래픽 처리 경험", "클라우드 인프라(AWS/GCP) 운영 경험"],
    benefits: ["재택근무 가능", "최신 장비 지원", "스톡옵션", "도서구입비 지원"],
  },
  "디자인": {
    responsibilities: ["프로덕트 UI/UX 디자인 설계", "디자인 시스템 구축 및 관리", "사용자 리서치 기반 개선안 도출"],
    qualifications: ["포트폴리오 제출 필수", "Figma 등 디자인 툴 활용 능력", "협업 기반 프로젝트 진행 경험"],
    preferred: ["프론트엔드 코드 이해도", "브랜딩/그래픽 디자인 경험"],
    benefits: ["자율출퇴근", "디자인 컨퍼런스 지원", "장비 지원"],
  },
  "마케팅·광고·MD": {
    responsibilities: ["채널별 마케팅 캠페인 기획 및 운영", "성과 데이터 분석 및 리포팅", "콘텐츠 기획 및 제작 협업"],
    qualifications: ["데이터 기반 의사결정 경험", "GA 등 분석 툴 활용 가능", "커뮤니케이션 역량"],
    preferred: ["퍼포먼스 마케팅 실무 경험", "이커머스 도메인 이해"],
    benefits: ["성과급", "자기계발비 지원", "유연근무제"],
  },
  "경영·사무": {
    responsibilities: ["부서별 업무 지원 및 문서 관리", "일정 조율 및 사내 커뮤니케이션", "각종 보고서 작성"],
    qualifications: ["MS Office 활용 능력", "꼼꼼함과 책임감", "원활한 커뮤니케이션 능력"],
    preferred: ["유관 업무 경험자 우대", "관련 자격증 소지자 우대"],
    benefits: ["4대보험", "명절 상여금", "정기 건강검진"],
  },
  "회계·세무·재무": {
    responsibilities: ["월/분기/연 결산 업무", "세무 신고 및 자금 관리", "재무제표 작성 및 분석"],
    qualifications: ["회계 관련 전공 또는 실무 경험", "전표처리 프로그램 활용 능력"],
    preferred: ["전산세무회계 자격증 소지자", "ERP 사용 경험"],
    benefits: ["자격수당 지급", "정시퇴근", "장기근속 포상"],
  },
  "영업·판매·무역": {
    responsibilities: ["신규 거래처 발굴 및 관리", "매출 목표 달성을 위한 영업 활동", "계약 협상 및 사후 관리"],
    qualifications: ["원활한 대인관계 및 협상력", "운전면허 소지자"],
    preferred: ["동종 업계 영업 경험자 우대"],
    benefits: ["인센티브 제도", "차량 유지비 지원", "법인카드 지급"],
  },
  "유통·물류": {
    responsibilities: ["입출고 및 재고 관리", "물류 프로세스 개선", "협력업체 커뮤니케이션"],
    qualifications: ["기본적인 PC 활용 능력", "체력적으로 성실한 분"],
    preferred: ["물류관리사 자격증 소지자", "지게차 면허 소지자"],
    benefits: ["교통비 지원", "식대 지원", "상여금"],
  },
  "생산·제조": {
    responsibilities: ["생산 라인 운영 및 품질 관리", "설비 점검 및 유지보수", "생산 실적 데이터 관리"],
    qualifications: ["관련 전공 또는 현장 경험자", "교대 근무 가능자"],
    preferred: ["관련 기사/산업기사 자격증 소지자"],
    benefits: ["기숙사 제공", "통근버스 운행", "명절선물"],
  },
  "고객상담·TM": {
    responsibilities: ["인/아웃바운드 고객 응대", "CS 이슈 처리 및 기록", "고객 만족도 개선 활동"],
    qualifications: ["원활한 커뮤니케이션 능력", "기본 컴퓨터 활용 능력"],
    preferred: ["콜센터/CS 유관 경험자 우대"],
    benefits: ["교육수당", "우수사원 포상", "정시출퇴근"],
  },
  "연구·R&D": {
    responsibilities: ["신제품/신기술 연구 개발", "실험 설계 및 데이터 분석", "연구 결과 문서화 및 특허 출원 지원"],
    qualifications: ["관련 전공 석사 이상 우대", "논문/특허 실적"],
    preferred: ["관련 분야 프로젝트 리딩 경험"],
    benefits: ["연구수당", "학회 참가비 지원", "특허출원 인센티브"],
  },
};

const DEFAULT_CONTENT: CategoryContent = {
  responsibilities: ["담당 직무 관련 실무 전반", "팀 목표 달성을 위한 협업"],
  qualifications: ["관련 분야 유경험자 또는 신입 지원 가능", "책임감 있게 업무를 수행할 수 있는 분"],
  preferred: ["유관 경험자 우대"],
  benefits: ["4대보험", "명절 상여금"],
};

function hashSeed(id: string): number {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  return hash;
}

export function getJobContent(job: JobPosting) {
  const content = CONTENT_BY_CATEGORY[job.jobCategoryName] ?? DEFAULT_CONTENT;
  const seed = hashSeed(job.id);
  return {
    ...content,
    viewCount: 80 + (seed % 1500),
    scrapCount: 5 + (seed % 120),
  };
}
