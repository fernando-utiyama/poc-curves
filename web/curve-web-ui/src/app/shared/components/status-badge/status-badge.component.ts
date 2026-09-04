import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="badge" [ngClass]="badgeClass">
      <span class="badge-dot"></span>
      {{ label }}
    </span>
  `,
  styles: [`
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 8px;
      border-radius: var(--radius-sm);
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.03em;
      white-space: nowrap;
    }
    .badge-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: currentColor;
    }
    .badge-publicada {
      background-color: var(--color-status-publicada-bg);
      color: var(--color-status-publicada);
      border: 1px solid var(--color-status-publicada);
    }
    .badge-aviso {
      background-color: var(--color-status-aviso-bg);
      color: var(--color-status-aviso);
      border: 1px solid var(--color-status-aviso);
    }
    .badge-em-risco {
      background-color: var(--color-status-em-risco-bg);
      color: var(--color-status-em-risco);
      border: 1px solid var(--color-status-em-risco);
      animation: pulse-border 1.5s infinite;
    }
    .badge-atrasada {
      background-color: var(--color-status-atrasada-bg);
      color: var(--color-status-atrasada);
      border: 1px solid var(--color-status-atrasada);
    }
    .badge-reprovada {
      background-color: var(--color-status-reprovada-bg);
      color: var(--color-status-reprovada);
      border: 1px solid var(--color-status-reprovada);
    }
    .badge-sem-dado {
      background-color: var(--color-status-sem-dado-bg);
      color: var(--color-status-sem-dado);
      border: 1px solid var(--color-status-sem-dado);
    }
    .badge-em-andamento {
      background-color: var(--color-status-em-andamento-bg);
      color: var(--color-status-em-andamento);
      border: 1px solid var(--color-status-em-andamento);
    }
    .badge-nao-iniciada {
      background-color: rgba(148, 163, 184, 0.1);
      color: #94a3b8;
      border: 1px solid #475569;
    }
    @keyframes pulse-border {
      0%, 100% { border-color: var(--color-status-em-risco); box-shadow: 0 0 4px var(--color-status-em-risco); }
      50% { border-color: transparent; box-shadow: none; }
    }
  `]
})
export class StatusBadgeComponent {
  @Input() status: string = '';

  get badgeClass(): string {
    switch (this.status?.toUpperCase()) {
      case 'PUBLICADA':
      case 'CONCLUIDA':
      case 'APROVADA':
        return 'badge-publicada';
      case 'PUBLICADA_COM_AVISO':
      case 'APROVADA_COM_AVISOS':
      case 'AVISO':
        return 'badge-aviso';
      case 'EM_RISCO':
        return 'badge-em-risco';
      case 'ATRASADA':
        return 'badge-atrasada';
      case 'REPROVADA':
      case 'FALHOU':
        return 'badge-reprovada';
      case 'SEM_DADO':
        return 'badge-sem-dado';
      case 'EM_ANDAMENTO':
      case 'EXECUTANDO':
      case 'CONSTRUINDO':
      case 'EM_REPROCESSAMENTO':
        return 'badge-em-andamento';
      case 'NAO_INICIADA':
      case 'PENDENTE':
      default:
        return 'badge-nao-iniciada';
    }
  }

  get label(): string {
    switch (this.status?.toUpperCase()) {
      case 'PUBLICADA': return 'PUBLICADA';
      case 'PUBLICADA_COM_AVISO': return 'PUBLICADA C/ AVISO';
      case 'EM_RISCO': return 'EM RISCO';
      case 'ATRASADA': return 'ATRASADA';
      case 'REPROVADA': return 'REPROVADA';
      case 'SEM_DADO': return 'SEM DADO';
      case 'EM_ANDAMENTO': return 'EM ANDAMENTO';
      case 'EXECUTANDO': return 'EXECUTANDO';
      case 'CONSTRUINDO': return 'CONSTRUINDO';
      case 'CONCLUIDA': return 'CONCLUÍDA';
      case 'FALHOU': return 'FALHOU';
      case 'NAO_INICIADA': return 'NÃO INICIADA';
      case 'PENDENTE': return 'PENDENTE';
      default: return this.status || '-';
    }
  }
}
