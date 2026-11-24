import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-modal',
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="fixed inset-0 z-20 flex items-start justify-center overflow-y-auto bg-slate-900/40 p-4 pt-20 backdrop-blur-sm">
      <div class="card w-full max-w-lg" role="dialog" aria-modal="true" [attr.aria-label]="title()">
        <div class="flex items-center justify-between border-b border-slate-200 px-6 py-4 dark:border-slate-800">
          <h2 class="font-semibold">{{ title() }}</h2>
          <button type="button" class="text-slate-400 hover:text-slate-700 dark:hover:text-slate-200" aria-label="Close" (click)="closed.emit()">&times;</button>
        </div>
        <div class="p-6">
          <ng-content />
        </div>
      </div>
    </div>
  `,
})
export class Modal {
  readonly title = input.required<string>();
  readonly closed = output();
}
