import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { BackfillStatusResponse, DatasetB3 } from '../../core/api/models.ts';

@Component({
  selector: 'app-backfill',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Backfill de Ingestão Histórica</h1>
          <p class="page-subtitle">Execução em lote para reconstituição de histórico em janela de datas na faixa de massa.</p>
        </div>
      </div>

      <div class="card-backfill">
        <h2 class="secao-titulo">Parâmetros do Backfill</h2>
        <div class="grid-inputs">
          <div class="campo">
            <label>Data Inicial:</label>
            <input type="date" [(ngModel)]="dataInicio" class="form-input" />
          </div>
          <div class="campo">
            <label>Data Final:</label>
            <input type="date" [(ngModel)]="dataFim" class="form-input" />
          </div>
          <div class="campo">
            <label>Limite de Concorrência:</label>
            <input type="number" [(ngModel)]="limiteConcorrencia" min="1" max="10" class="form-input" style="width: 100px;" />
          </div>
        </div>

        <div class="campo" style="margin-top: 16px;">
          <label>Datasets a Reconstituir:</label>
          <div class="chk-group">
            <label><input type="checkbox" [(ngModel)]="datasetBvbg086" /> BVBG.086 (Preços)</label>
            <label><input type="checkbox" [(ngModel)]="datasetBvbg028" /> BVBG.028 (Cadastro)</label>
            <label><input type="checkbox" [(ngModel)]="datasetPrDi1" /> PR_DI1</label>
            <label><input type="checkbox" [(ngModel)]="datasetTaxasRef" /> Taxas de Referência B3</label>
          </div>
        </div>

        <div class="card-acoes">
          <button
            class="btn btn-primary"
            [disabled]="iniciando() || statusAtivo()?.status === 'EM_ANDAMENTO' || !authService.isOperator()"
            [title]="authService.isOperator() ? 'Iniciar processo de backfill' : authService.getPermissionExplanation('CURVE_OPERATOR')"
            (click)="iniciar()">
            {{ iniciando() ? 'Iniciando...' : '🚀 Iniciar Backfill' }}
          </button>
        </div>
      </div>

      <!-- Progresso do Backfill Ativo -->
      @if (statusAtivo(); as s) {
        <div class="card-progresso">
          <div class="progresso-topo">
            <div>
              <h3>Status do Backfill: <code>{{ s.id }}</code></h3>
              <span class="badge-status" [ngClass]="'status-' + s.status.toLowerCase()">{{ s.status }}</span>
            </div>
            @if (s.status === 'EM_ANDAMENTO') {
              <button
                class="btn btn-secondary btn-interromper"
                [disabled]="!authService.isOperator()"
                (click)="interromper(s.id)">
                ⏹️ Interromper Backfill
              </button>
            }
          </div>

          <div class="barra-container">
            <div class="barra-progresso" [style.width.%]="calcularPercentual(s)"></div>
          </div>

          <div class="metricas-progresso">
            <div class="p-card">
              <span class="p-rotulo">Total Dias Úteis</span>
              <span class="p-num">{{ s.totalDias }}</span>
            </div>
            <div class="p-card card-sucesso">
              <span class="p-rotulo">Concluídos</span>
              <span class="p-num">{{ s.concluidos }}</span>
            </div>
            <div class="p-card card-semdado">
              <span class="p-rotulo">Sem Dado</span>
              <span class="p-num">{{ s.semDado }}</span>
            </div>
            <div class="p-card card-falha">
              <span class="p-rotulo">Falhas</span>
              <span class="p-num">{{ s.falhas }}</span>
            </div>
            <div class="p-card card-pendente">
              <span class="p-rotulo">Pendentes</span>
              <span class="p-num">{{ s.pendentes }}</span>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-container {
      padding: var(--space-xl);
      max-width: 1000px;
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
    .card-backfill, .card-progresso {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
      margin-bottom: var(--space-xl);
    }
    .secao-titulo {
      font-size: 15px;
      font-weight: 600;
      margin-bottom: var(--space-md);
    }
    .grid-inputs {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
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
    .chk-group {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      font-size: 13px;
    }
    .chk-group label {
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
    }
    .card-acoes {
      margin-top: 20px;
      display: flex;
      justify-content: flex-end;
    }
    .progresso-topo {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .progresso-topo h3 {
      font-size: 15px;
      font-weight: 600;
    }
    .badge-status {
      font-size: 11px;
      padding: 2px 6px;
      border-radius: 4px;
      font-weight: 600;
    }
    .status-em_andamento { background-color: rgba(59, 130, 246, 0.2); color: #3b82f6; }
    .status-concluido { background-color: rgba(16, 185, 129, 0.2); color: #10b981; }
    .status-interrompido { background-color: rgba(245, 158, 11, 0.2); color: #f59e0b; }
    .barra-container {
      width: 100%;
      height: 8px;
      background-color: var(--color-bg-page);
      border-radius: 4px;
      overflow: hidden;
      margin-bottom: 16px;
    }
    .barra-progresso {
      height: 100%;
      background-color: var(--color-primary);
      transition: width 0.3s ease;
    }
    .metricas-progresso {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
      gap: 12px;
    }
    .p-card {
      background-color: var(--color-bg-page);
      padding: 10px;
      border-radius: var(--radius-sm);
      display: flex;
      flex-direction: column;
    }
    .p-rotulo {
      font-size: 11px;
      color: var(--color-text-muted);
      text-transform: uppercase;
      font-weight: 600;
    }
    .p-num {
      font-size: 20px;
      font-weight: 700;
      font-family: var(--font-mono);
      margin-top: 2px;
    }
    .card-sucesso .p-num { color: var(--color-status-publicada); }
    .card-semdado .p-num { color: var(--color-status-sem-dado); }
    .card-falha .p-num { color: var(--color-status-atrasada); }
    .btn-interromper {
      color: #ef4444;
      border-color: #ef4444;
    }
  `]
})
export class BackfillComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);

  public dataInicio: string = '';
  public dataFim: string = '';
  public limiteConcorrencia: number = 3;

  public datasetBvbg086: boolean = true;
  public datasetBvbg028: boolean = true;
  public datasetPrDi1: boolean = true;
  public datasetTaxasRef: boolean = true;

  public iniciando = signal<boolean>(false);
  public statusAtivo = signal<BackfillStatusResponse | null>(null);

  ngOnInit(): void {
    const hoje = new Date();
    this.dataFim = hoje.toISOString().substring(0, 10);
    const trintaDiasAtras = new Date(hoje.getTime() - 30 * 24 * 60 * 60 * 1000);
    this.dataInicio = trintaDiasAtras.toISOString().substring(0, 10);
  }

  public iniciar(): void {
    if (!this.authService.isOperator()) return;

    this.iniciando.set(true);
    const datasets: DatasetB3[] = [];
    if (this.datasetBvbg086) datasets.push('BVBG_086');
    if (this.datasetBvbg028) datasets.push('BVBG_028');
    if (this.datasetPrDi1) datasets.push('PR_DI1');
    if (this.datasetTaxasRef) datasets.push('TAXAS_REFERENCIA');

    this.bffClient.iniciarBackfill({
      dataInicio: this.dataInicio,
      dataFim: this.dataFim,
      datasets,
      limiteConcorrencia: this.limiteConcorrencia
    }).subscribe({
      next: (resp) => {
        this.statusAtivo.set(resp);
        this.iniciando.set(false);
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao iniciar backfill.');
        this.iniciando.set(false);
      }
    });
  }

  public interromper(id: string): void {
    if (!this.authService.isOperator()) return;

    this.bffClient.interromperBackfill(id).subscribe({
      next: (resp) => {
        this.statusAtivo.set(resp);
      }
    });
  }

  public calcularPercentual(s: BackfillStatusResponse): number {
    if (!s.totalDias || s.totalDias === 0) return 0;
    const concluidos = (s.concluidos || 0) + (s.semDado || 0) + (s.falhas || 0);
    return Math.min(100, Math.round((concluidos / s.totalDias) * 100));
  }
}
