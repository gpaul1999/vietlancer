export type Role = 'CLIENT' | 'FREELANCER' | 'ADMIN';

export interface User {
  id: number;
  /** Chỉ có khi xem hồ sơ của chính mình (/auth/me) — hồ sơ public không trả email. */
  email?: string;
  fullName: string;
  role: Role;
  bio?: string;
  skills: string[];
  hourlyRate?: number;
  avatarUrl?: string;
  createdAt: string;
}

export interface Topic {
  id: number;
  slug: string;
  name: string;
  nameEn?: string;
  icon?: string;
}

export type JobStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface Job {
  id: number;
  title: string;
  description: string;
  budgetMin?: number;
  budgetMax?: number;
  deadline?: string;
  status: JobStatus;
  topics: { slug: string; name: string; icon?: string }[];
  aiEngine?: string;
  aiExplanation?: string;
  client: { id: number; fullName: string; avatarUrl?: string; premium: boolean };
  assignedFreelancerId?: number;
  escrowAmount?: number;
  milestoneBased: boolean;
  bidCount: number;
  createdAt: string;
}

export type MilestoneStatus = 'PENDING' | 'FUNDED' | 'SUBMITTED' | 'RELEASED' | 'CANCELLED';

export interface Milestone {
  id: number;
  jobId: number;
  title: string;
  amount: number;
  dueDate?: string;
  status: MilestoneStatus;
  createdAt: string;
}

export type DisputeStatus = 'OPEN' | 'RESOLVED' | 'WITHDRAWN';

export interface Dispute {
  id: number;
  jobId: number;
  jobTitle: string;
  raisedById: number;
  raisedByName: string;
  reason: string;
  status: DisputeStatus;
  heldAmount: number;
  amountToFreelancer?: number;
  resolutionNote?: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface JobSearchResult {
  jobs: Job[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export type BidStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export interface Bid {
  id: number;
  jobId: number;
  jobTitle: string;
  freelancer: { id: number; fullName: string; avatarUrl?: string; skills?: string; premium: boolean };
  amount: number;
  deliveryDays: number;
  coverLetter: string;
  status: BidStatus;
  createdAt: string;
}

export interface PreviewTopic {
  slug: string;
  name: string;
  icon?: string;
  confidence: number;
  reason: string;
}

export interface ClassifyPreview {
  topics: PreviewTopic[];
  explanation: string;
  engine: string;
}

export interface Conversation {
  id: number;
  jobId: number;
  jobTitle: string;
  client: { id: number; fullName: string; avatarUrl?: string };
  freelancer: { id: number; fullName: string; avatarUrl?: string };
  lastMessage?: string;
  createdAt: string;
}

export interface Message {
  id: number;
  senderId: number;
  content: string;
  createdAt: string;
}

export interface Review {
  id: number;
  jobId: number;
  jobTitle: string;
  reviewerId: number;
  reviewerName: string;
  rating: number;
  comment?: string;
  createdAt: string;
}

export interface RatingSummary {
  average: number | null;
  count: number;
  reviews: Review[];
}

export interface Wallet {
  balance: number;
  escrowBalance: number;
}

export interface WalletTransaction {
  id: number;
  type: string;
  amount: number;
  note?: string;
  createdAt: string;
}

export interface Notification {
  id: number;
  type: 'NEW_BID' | 'BID_ACCEPTED' | 'BID_REJECTED' | 'JOB_COMPLETED' | 'NEW_MESSAGE' | 'NEW_REVIEW';
  message: string;
  link?: string;
  read: boolean;
  createdAt: string;
}

export interface FreelancerCard {
  id: number;
  fullName: string;
  bio?: string;
  skills: string[];
  hourlyRate?: number;
  avatarUrl?: string;
  premium: boolean;
  ratingAvg: number | null;
  ratingCount: number;
}

export interface FreelancerSearchResult {
  freelancers: FreelancerCard[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export interface SubscriptionStatus {
  premium: boolean;
  plan?: string;
  expiresAt?: string;
  price: number;
}
