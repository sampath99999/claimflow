import { Component, computed, input } from '@angular/core';

const STYLES: Record<string, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/10 dark:text-emerald-300',
  SUSPENDED: 'bg-amber-50 text-amber-800 ring-amber-600/20 dark:bg-amber-500/10 dark:text-amber-300',
  TERMINATED: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-500/10 dark:text-slate-300',
  RETIRED: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-500/10 dark:text-slate-300',
};

@Component({
  selector: 'app-status-pill',
  template: `<span class="inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset" [class]="style()">{{ label() }}</span>`,
})
export class StatusPill {
  readonly status = input.required<string>();
  protected readonly style = computed(() => STYLES[this.status()] ?? STYLES['TERMINATED']);
  protected readonly label = computed(() => this.status().charAt(0) + this.status().slice(1).toLowerCase());
}
