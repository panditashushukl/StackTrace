export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type HistoryScope = 'all' | 'mine';

export interface StackTraceRequest {
  rawStackTrace: string;
  languageHint?: string | null;
  codeContext?: string | null;
}

export interface FailureLocation {
  fileName: string;
  className: string;
  methodName: string;
  lineNumber: number;
}

export interface CodeFix {
  language: string;
  explanation: string;
  snippet: string;
}

export interface DiagnosticReport {
  detectedLanguage: string;
  errorType: string;
  severity: Severity;
  summary: string;
  rootCause: string;
  failureLocation: FailureLocation;
  stepByStepFix: string[];
  codeFix: CodeFix;
  preventiveRecommendations: string[];
}

export interface StackTraceResponse {
  id: string;
  rawStackTrace: string;
  codeContext?: string | null;
  detectedLanguage: string;
  errorType: string;
  severity: Severity;
  analysisResult: DiagnosticReport;
  markdownReport: string;
  modelUsed: string;
  createdAt: string;
  canDelete: boolean;
}

export interface AnalysisSummary {
  id: string;
  detectedLanguage: string;
  errorType: string;
  severity: Severity;
  summary: string;
  fileName: string;
  modelUsed: string;
  createdAt: string;
  canDelete: boolean;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
