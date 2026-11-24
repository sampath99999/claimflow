import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, map, of, switchMap, tap } from 'rxjs';
import { AdminApiService } from '../../core/admin-api.service';
import { Member, MemberInput, MemberStatus, Page } from '../../core/models';
import { describeError } from '../../core/problem';
import { Modal } from '../../shared/modal';
import { Pager } from '../../shared/pager';
import { SortHeader, SortState } from '../../shared/sort-header';
import { StatusPill } from '../../shared/status-pill';

@Component({
  selector: 'app-members-page',
  imports: [ReactiveFormsModule, DatePipe, Pager, SortHeader, StatusPill, Modal],
  templateUrl: './members-page.html',
})
export class MembersPage {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly statuses: MemberStatus[] = ['ACTIVE', 'SUSPENDED', 'TERMINATED'];
  protected readonly search = signal('');
  protected readonly status = signal<MemberStatus | ''>('');
  protected readonly planCode = signal('');
  protected readonly page = signal(0);
  protected readonly sort = signal<SortState>({ field: 'lastName', direction: 'asc' });
  protected readonly result = signal<Page<Member> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private readonly reload = signal(0);

  protected readonly plans = toSignal(
    this.api.plans({ page: 0, size: 100, sort: 'code,asc' }).pipe(
      map((page) => page.content),
      catchError(() => of([])),
    ),
    { initialValue: [] },
  );
  protected readonly openPlans = computed(() => this.plans().filter((p) => p.active));

  protected readonly editing = signal<Member | 'new' | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly form = this.fb.group({
    firstName: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(80)]),
    lastName: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(80)]),
    dateOfBirth: this.fb.nonNullable.control('', Validators.required),
    email: this.fb.control<string | null>(null, Validators.email),
    planCode: this.fb.nonNullable.control('', Validators.required),
    coverageStart: this.fb.nonNullable.control('', Validators.required),
    coverageEnd: this.fb.control<string | null>(null),
    status: this.fb.nonNullable.control<MemberStatus>('ACTIVE'),
  });

  private readonly query = computed(() => ({
    q: this.search().trim(),
    status: this.status(),
    planCode: this.planCode(),
    page: this.page(),
    size: 10,
    sort: `${this.sort().field},${this.sort().direction}`,
    reload: this.reload(),
  }));

  constructor() {
    toObservable(this.query)
      .pipe(
        debounceTime(200),
        tap(() => this.loading.set(true)),
        switchMap((query) =>
          this.api
            .members({ q: query.q, status: query.status, planCode: query.planCode, page: query.page, size: query.size, sort: query.sort })
            .pipe(
              tap(() => this.error.set(null)),
              catchError((e: unknown) => {
                this.error.set(describeError(e));
                return of(null);
              }),
            ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.result.set(page);
        this.loading.set(false);
      });
  }

  protected isOpenPlan(code: string): boolean {
    return this.openPlans().some((p) => p.code === code);
  }

  protected setFilter(apply: () => void): void {
    this.page.set(0);
    apply();
  }

  protected onSort(sort: SortState): void {
    this.page.set(0);
    this.sort.set(sort);
  }

  protected openNew(): void {
    this.form.reset({ status: 'ACTIVE', coverageStart: new Date().toISOString().slice(0, 10) });
    this.saveError.set(null);
    this.editing.set('new');
  }

  protected openEdit(member: Member): void {
    this.form.reset({ ...member, planCode: member.plan.code });
    this.saveError.set(null);
    this.editing.set(member);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const input: MemberInput = { ...value, email: value.email || null, coverageEnd: value.coverageEnd || null };
    const target = this.editing();
    this.saving.set(true);
    const request = target === 'new' || target === null ? this.api.createMember(input) : this.api.updateMember(target.id, input);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(null);
        this.reload.update((n) => n + 1);
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.saveError.set(describeError(e));
      },
    });
  }

  protected invalid(name: 'firstName' | 'lastName' | 'dateOfBirth' | 'email' | 'planCode' | 'coverageStart'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}
