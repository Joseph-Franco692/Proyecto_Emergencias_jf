import { DOCUMENT } from '@angular/common';
import { Inject, Injectable, signal } from '@angular/core';

export type AppTheme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<AppTheme>('dark');

  constructor(@Inject(DOCUMENT) private document: Document) {
    const saved = localStorage.getItem('gestion-bomberil-theme') as AppTheme | null;
    const preferred: AppTheme = window.matchMedia?.('(prefers-color-scheme: light)').matches
      ? 'light'
      : 'dark';
    this.setTheme(saved === 'light' || saved === 'dark' ? saved : preferred);
  }

  toggle(): void {
    this.setTheme(this.theme() === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: AppTheme): void {
    this.theme.set(theme);
    this.document.documentElement.dataset['theme'] = theme;
    this.document.documentElement.style.colorScheme = theme;
    localStorage.setItem('gestion-bomberil-theme', theme);
  }
}
