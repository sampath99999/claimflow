import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from './models';

export function describeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Could not reach the ClaimFlow gateway.';
    }
    const problem = error.error as Partial<ProblemDetail> | null;
    const fields = problem?.errors?.map((e) => `${e.field} ${e.message}`).join('; ');
    return [problem?.detail ?? problem?.title ?? `Request failed (${error.status})`, fields].filter(Boolean).join(': ');
  }
  return error instanceof Error ? error.message : 'Something went wrong';
}
