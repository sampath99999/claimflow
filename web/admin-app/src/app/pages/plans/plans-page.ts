import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { AdminApiService } from '../../core/admin-api.service';
import { Page, Plan, PlanInput, PlanType } from '../../core/models';
import { describeError } from '../../core/problem';
import { Modal } from '../../shared/modal';
import { Pager } from '../../shared/pager';
import { SortHeader, SortState } from '../../shared/sort-header';
import { StatusPill } from '../../shared/status-pill';

@Component({
  selector: 'app-plans-page',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, Pager, SortHeader, StatusPill, Modal],
  templateUrl: './plans-page.html',
})
export class PlansPage {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly planTypes: PlanType[] = ['HMO', 'PPO', 'EPO', 'HDHP'];
  protected readonly search = signal('');
  protected readonly activeOnly = signal(false);
  protected readonly page = signal(0);
  protected readonly sort = signal<SortState>({ field: 'code', direction: 'asc' });
  protected readonly result = signal<Page<Plan> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private readonly reload = signal(0);

  protected readonly editing = signal<Plan | 'new' | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z0-9-]{3,32}$/)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    type: ['PPO' as PlanType, Validators.required],
    annualDeductible: [0, [Validators.required, Validators.min(0)]],
    coinsurancePercent: [20, [Validators.required, Validators.min(0), Validators.max(100)]],
    outOfPocketMax: [0, [Validators.required, Validators.min(0)]],
    active: [true],
  });

  private readonly query = computed(() => ({
    q: this.search().trim(),
    activeOnly: this.activeOnly(),
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
          this.api.plans({ q: query.q, activeOnly: query.activeOnly, page: query.page, size: query.size, sort: query.sort }).pipe(
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

  protected onSearch(value: string): void {
    this.page.set(0);
    this.search.set(value);
  }

  protected onActiveOnly(value: boolean): void {
    this.page.set(0);
    this.activeOnly.set(value);
  }

  protected onSort(sort: SortState): void {
    this.page.set(0);
    this.sort.set(sort);
  }

  protected openNew(): void {
    this.form.reset();
    this.form.controls.code.enable();
    this.saveError.set(null);
    this.editing.set('new');
  }

  protected openEdit(plan: Plan): void {
    this.form.reset({ ...plan });
    this.form.controls.code.disable();
    this.saveError.set(null);
    this.editing.set(plan);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const target = this.editing();
    const value = this.form.getRawValue();
    const input: PlanInput = { ...value, code: value.code.toUpperCase() };
    this.saving.set(true);
    const request = target === 'new' || target === null ? this.api.createPlan(input) : this.api.updatePlan(target.id, input);
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

  protected invalid(name: 'code' | 'name' | 'coinsurancePercent'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}
