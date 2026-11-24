import { Component, computed, input, output } from '@angular/core';

export interface SortState {
  field: string;
  direction: 'asc' | 'desc';
}

@Component({
  selector: 'app-sort-header',
  template: `
    <button type="button" class="inline-flex items-center gap-1 uppercase hover:text-slate-900 dark:hover:text-white" (click)="toggle()">
      {{ label() }}
      <span aria-hidden="true" class="text-[10px]">{{ indicator() }}</span>
    </button>
  `,
})
export class SortHeader {
  readonly field = input.required<string>();
  readonly label = input.required<string>();
  readonly sort = input.required<SortState>();
  readonly sortChange = output<SortState>();

  protected readonly indicator = computed(() => {
    const sort = this.sort();
    if (sort.field !== this.field()) {
      return '';
    }
    return sort.direction === 'asc' ? '▲' : '▼';
  });

  protected toggle(): void {
    const sort = this.sort();
    const direction = sort.field === this.field() && sort.direction === 'asc' ? 'desc' : 'asc';
    this.sortChange.emit({ field: this.field(), direction });
  }
}
