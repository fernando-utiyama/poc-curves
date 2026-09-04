import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { InterpolacaoResponse } from '../../core/api/models.ts';
import { TaxaFormatPipe, FatorDescontoPipe } from '../../shared/pipes/taxa-format.pipe.ts';

@Component({
  selector: 'app-interpolacao',
  standalone: true,
  imports: [CommonModule, FormsModule, TaxaFormatPipe, FatorDescontoPipe],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Consulta de Interpolação Ad-hoc</h1>
          <p class="page-subtitle">Amostragem síncrona de taxas e fatores de desconto para prazos arbitrários em lote.</p>
        </div>
      </div>

      <div class="card-consulta">
        <div class="grid-inputs">
          <div class="campo">
            <label>Código da Curva:</label>
            <input type="text" [(ngModel)]="codigoCurva" class="form-input" placeholder="Ex: PRE" />
          </div>
          <div class="campo">
            <label>Data de Referência:</label>
            <input type="date" [(ngModel)]="dataReferencia" class="form-input" />
          </div>
          <div class="campo">
            <label>Prazos em Dias Úteis (separados por vírgula ou espaço):</label>
            <input type="text" [(ngModel)]="prazosTexto" class="form-input" placeholder="Ex: 21, 42, 63, 126, 252, 504" />
          </div>
        </div>

        <div class="card-acoes">
          <button class="btn btn-primary" [disabled]="loading()" (click)="executarInterpolacao()">
            {{ loading() ? 'Interpolando...' : '🔍 Calcular Prazos' }}
          </button>
        </div>
      </div>

      @if (resultado(); as r) {
        <div class="resultado-box">
          <div class="resultado-header">
            <span class="resultado-titulo">Resultados da Interpolação — Curva {{ r.codigoCurva }} (Versão v{{ r.versaoUtilizada }})</span>
            <span class="interpolador-badge">Método: {{ r.interpoladorUtilizado || 'Padrão da Curva' }}</span>
          </div>

          <table class="data-table">
            <thead>
              <tr>
                <th class="th-num">Prazo Consultado (DU)</th>
                <th class="th-num">Taxa Interpolada (% a.a.)</th>
                <th class="th-num">Fator de Desconto</th>
                <th>Status da Amostragem</th>
              </tr>
            </thead>
            <tbody>
              @for (item of r.resultados; track item.prazoDiasUteis) {
                <tr [class.linha-erro]="item.status === 'ERRO_FORA_INTERVALO'" [class.linha-extrapolado]="item.status === 'EXTRAPOLADO'">
                  <td class="td-num tabular-nums"><strong>{{ item.prazoDiasUteis }}</strong></td>
                  <td class="td-num tabular-nums">
                    @if (item.status === 'ERRO_FORA_INTERVALO') {
                      <span class="erro-txt">{{ item.erroMensagem || 'Fora do intervalo (Política Estrita)' }}</span>
                    } @else {
                      <span class="taxa-destaque">{{ item.taxa | taxaFormat }}</span>
                    }
                  </td>
                  <td class="td-num tabular-nums">
                    {{ item.status === 'ERRO_FORA_INTERVALO' ? '-' : (item.fatorDesconto | fatorDesconto) }}
                  </td>
                  <td>
                    @if (item.status === 'VERTICE_EXATO') {
                      <span class="badge-amostragem badge-exato">Vértice Exato</span>
                    } @else if (item.status === 'INTERPOLADO') {
                      <span class="badge-amostragem badge-interpolado">Interpolado</span>
                    } @else if (item.status === 'EXTRAPOLADO') {
                      <span class="badge-amostragem badge-extrapolado">Extrapolado</span>
                    } @else if (item.status === 'ERRO_FORA_INTERVALO') {
                      <span class="badge-amostragem badge-falha">Fora do Intervalo</span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-container {
      padding: var(--space-xl);
      max-width: 1200px;
      margin: 0 auto;
    }
    .page-header {
      margin-bottom: var(--space-lg);
    }
    .page-title {
      font-size: 22px;
      font-weight: 700;
    }
    .page-subtitle {
      font-size: 13px;
      color: var(--color-text-muted);
    }
    .card-consulta {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
      margin-bottom: var(--space-xl);
    }
    .grid-inputs {
      display: grid;
      grid-template-columns: 180px 200px 1fr;
      gap: 16px;
      align-items: flex-end;
    }
    @media (max-width: 900px) {
      .grid-inputs { grid-template-columns: 1fr; }
    }
    .campo {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .campo label {
      font-size: 12px;
      font-weight: 600;
      color: var(--color-text-secondary);
    }
    .card-acoes {
      margin-top: 16px;
      display: flex;
      justify-content: flex-end;
    }
    .resultado-box {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-md);
    }
    .resultado-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: var(--space-md);
      border-bottom: 1px solid var(--color-border);
      padding-bottom: 8px;
    }
    .resultado-titulo {
      font-size: 15px;
      font-weight: 600;
    }
    .interpolador-badge {
      font-size: 11px;
      background-color: #334155;
      padding: 2px 8px;
      border-radius: 4px;
      color: #cbd5e1;
    }
    .taxa-destaque {
      color: #38bdf8;
      font-weight: 600;
    }
    .erro-txt {
      color: #ef4444;
      font-size: 12px;
    }
    .badge-amostragem {
      font-size: 11px;
      padding: 2px 6px;
      border-radius: 4px;
      font-weight: 600;
    }
    .badge-exato { background-color: rgba(16, 185, 129, 0.15); color: #10b981; }
    .badge-interpolado { background-color: rgba(59, 130, 246, 0.15); color: #3b82f6; }
    .badge-extrapolado { background-color: rgba(245, 158, 11, 0.15); color: #f59e0b; }
    .badge-falha { background-color: rgba(239, 68, 68, 0.15); color: #ef4444; }
    .linha-erro { background-color: rgba(239, 68, 68, 0.05); }
    .linha-extrapolado { background-color: rgba(245, 158, 11, 0.05); }
  `]
})
export class InterpolacaoComponent implements OnInit {
  private bffClient = inject(CurveBffClientService);

  public codigoCurva: string = 'PRE';
  public dataReferencia: string = new Date().toISOString().substring(0, 10);
  public prazosTexto: string = '21, 42, 63, 126, 252, 504, 756, 1008';

  public loading = signal<boolean>(false);
  public resultado = signal<InterpolacaoResponse | null>(null);

  ngOnInit(): void {}

  public executarInterpolacao(): void {
    this.loading.set(true);
    const prazos = this.prazosTexto
      .split(/[\s,]+/)
      .map(p => parseInt(p.trim(), 10))
      .filter(p => !isNaN(p) && p > 0);

    this.bffClient.interpolarCurva(this.codigoCurva, {
      dataReferencia: this.dataReferencia,
      prazosDiasUteis: prazos
    }).subscribe({
      next: (resp) => {
        this.resultado.set(resp);
        this.loading.set(false);
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao executar interpolação.');
        this.loading.set(false);
      }
    });
  }
}
