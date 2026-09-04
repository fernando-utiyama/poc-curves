import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-secao-degradada',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="degradada-container">
      <div class="degradada-header">
        <span class="icone">⚠️</span>
        <span class="titulo">{{ titulo }} temporariamente indisponível</span>
      </div>
      <p class="motivo">{{ motivo || 'O serviço responsável não respondeu a tempo ou está indisponível.' }}</p>
      @if (permiteRepetir) {
        <button class="btn btn-secondary btn-sm" (click)="repetir.emit()">
          🔄 Tentar Novamente
        </button>
      }
    </div>
  `,
  styles: [`
    .degradada-container {
      background-color: var(--color-status-degradada-bg);
      border: 1px dashed var(--color-status-degradada);
      border-radius: var(--radius-md);
      padding: var(--space-md) var(--space-lg);
      margin: var(--space-md) 0;
      color: var(--color-text-secondary);
    }
    .degradada-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;
      font-weight: 600;
      color: #fef08a;
    }
    .motivo {
      font-size: 13px;
      margin-bottom: 8px;
      color: var(--color-text-muted);
    }
    .btn-sm {
      padding: 4px 10px;
      font-size: 12px;
    }
  `]
})
export class SecaoDegradadaComponent {
  @Input() titulo: string = 'Seção';
  @Input() motivo: string = '';
  @Input() permiteRepetir: boolean = true;
  @Output() repetir = new EventEmitter<void>();
}
