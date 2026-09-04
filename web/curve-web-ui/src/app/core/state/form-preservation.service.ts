import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class FormPreservationService {
  private drafts: Map<string, unknown> = new Map();

  public saveDraft(formKey: string, data: unknown): void {
    this.drafts.set(formKey, data);
    try {
      sessionStorage.setItem(`curve_draft_${formKey}`, JSON.stringify(data));
    } catch {
      // Ignore storage errors in restricted contexts
    }
  }

  public getDraft<T>(formKey: string): T | null {
    if (this.drafts.has(formKey)) {
      return this.drafts.get(formKey) as T;
    }
    try {
      const stored = sessionStorage.getItem(`curve_draft_${formKey}`);
      if (stored) {
        const parsed = JSON.parse(stored) as T;
        this.drafts.set(formKey, parsed);
        return parsed;
      }
    } catch {
      // Ignore
    }
    return null;
  }

  public clearDraft(formKey: string): void {
    this.drafts.delete(formKey);
    try {
      sessionStorage.removeItem(`curve_draft_${formKey}`);
    } catch {
      // Ignore
    }
  }
}
