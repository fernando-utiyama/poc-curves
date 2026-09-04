import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AlertasGlobalService } from '../../../core/state/alertas-global.service.ts';

@Component({
  selector: 'app-alerta-banner',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    @if (alertasService.isErrorState()) {
      <div class="alerta-box alerta-erro">
        <span class="alerta-icone">⚠️</span>
        <span class="alerta-texto">Aviso: Estado de alertas do sistema indisponível ou com falha de conexão.</span>
        <button class="btn-tentar" (click)="alertasService.carregar()">Tentar Reconectar</button>
      </div>
    } @else if (alertasService.temAlertaAtivo()) {
      <div class="alerta-box" [ngClass]="alertasService.isEnvelhecido() || alertasService.curvasAtrasadas() > 0 ? 'alerta-critico' : 'alerta-aviso'">
        <div class="alerta-conteudo">
          <span class="alerta-icone">🔔</span>
          <div class="alerta-detalhes">
            @if (alertasService.gruposPendentesDlq() > 0) {
              <span class="alerta-item">
                <strong>{{ alertasService.gruposPendentesDlq() }}</strong> grupo(s) em Dead-Letter ({{ alertasService.totalMensagensDlq() }} msgs)
                @if (alertasService.isEnvelhecido()) {
                  <span class="badge-envelhecido">Falha há {{ alertasService.idadeMaisAntigaMinutos() }} min</span>
                }
              </span>
            }
            @if (alertasService.curvasEmRisco() > 0) {
              <span class="alerta-item risco">
                <strong>{{ alertasService.curvasEmRisco() }}</strong> curva(s) em risco de corte
              </span>
            }
            @if (alertasService.curvasAtrasadas() > 0) {
              <span class="alerta-item atraso">
                <strong>{{ alertasService.curvasAtrasadas() }}</strong> curva(s) atrasadas
              </span>
            }
          </div>
        </div>
        <a routerLink="/pendencias-dlq" class="btn-ver-pendencias">Ver Pendências &rarr;</a>
      </div>
    }
  `,
  styles: [`
    .alerta-box {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 16px;
      font-size: 13px;
      border-bottom: 1px solid var(--color-border);
      gap: 12px;
    }
    .alerta-aviso {
      background-color: rgba(245, 158, 11, 0.15);
      border-color: var(--color-status-aviso);
      color: #fef08a;
    }
    .alerta-critico {
      background-color: rgba(239, 68, 68, 0.2);
      border-color: var(--color-status-atrasada);
      color: #fca5a5;
    }
    .alerta-erro {
      background-color: rgba(148, 163, 184, 0.15);
      border-color: #64748b;
      color: #cbd5e1;
    }
    .alerta-conteudo {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .alerta-detalhes {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }
    .badge-envelhecido {
      background-color: #dc2626;
      color: white;
      padding: 1px 6px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      margin-left: 6px;
    }
    .alerta-item.risco {
      color: #fdba74;
    }
    .alerta-item.atraso {
      color: #f87171;
    }
    .btn-ver-pendencias {
      color: inherit;
      text-decoration: underline;
      font-weight: 600;
      white-space: nowrap;
    }
    .btn-tentar {
      background: none;
      border: 1px solid currentColor;
      color: inherit;
      padding: 2px 8px;
      border-radius: 4px;
      cursor: pointer;
    }
  `]
})
export class AlertaBannerComponent {
  public alertasService = inject(AlertasGlobalService);
}
