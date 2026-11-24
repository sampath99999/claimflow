import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-pager',
  template: `
    @if (totalPages() > 0) {
      <div class="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm dark:border-slate-800">
        <span class="text-slate-500 dark:text-slate-400">
          Page {{ page() + 1 }} of {{ totalPages() }} &middot; {{ totalElements() }} {{ noun() }}
        </span>
        <div class="flex gap-2">
          <button type="button" class="btn-secondary px-2.5 py-1" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">Previous</button>
          <button type="button" class="btn-secondary px-2.5 py-1" [disabled]="page() + 1 >= totalPages()" (click)="pageChange.emit(page() + 1)">Next</button>
        </div>
      </div>
    }
  `,
})
export class Pager {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly noun = input('rows');
  readonly pageChange = output<number>();
}
