import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Member, MemberInput, MemberStatus, Page, Plan, PlanInput } from './models';

export interface PageQuery {
  q?: string;
  page: number;
  size: number;
  sort: string;
}

function params(query: Record<string, string | number | boolean | undefined | null>): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      result = result.set(key, String(value));
    }
  }
  return result;
}

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api`;

  plans(query: PageQuery & { activeOnly?: boolean }): Observable<Page<Plan>> {
    return this.http.get<Page<Plan>>(`${this.base}/plans`, { params: params({ ...query }) });
  }

  createPlan(input: PlanInput): Observable<Plan> {
    return this.http.post<Plan>(`${this.base}/plans`, input);
  }

  updatePlan(id: string, input: PlanInput): Observable<Plan> {
    return this.http.put<Plan>(`${this.base}/plans/${id}`, input);
  }

  members(query: PageQuery & { status?: MemberStatus | ''; planCode?: string }): Observable<Page<Member>> {
    return this.http.get<Page<Member>>(`${this.base}/members`, { params: params({ ...query }) });
  }

  createMember(input: MemberInput): Observable<Member> {
    return this.http.post<Member>(`${this.base}/members`, input);
  }

  updateMember(id: string, input: MemberInput): Observable<Member> {
    return this.http.put<Member>(`${this.base}/members/${id}`, input);
  }
}
