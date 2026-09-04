import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { DatasetB3, DisparoIngestaoResponse } from '../../core/api/models.ts';

@Component({
  selector: 'app-disparo-manual',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Disparo Manual de Ingestão de Mercado</h1>
          <p class="page-subtitle">Dispare a aquisição de dados brutos da B3 na faixa prioritária e acompanhe a execução.</p>
        </div>
      </div>

      <div class="card-formulario">
        <div class="campo">
          <label>Data de Referência do Pregão:</label>
          <input type="date" [(ngModel)]="dataReferencia" (change)="verificarPregao()" class="form-input" style="max-width: 250px;" />
          @if (avisoFeriado()) {
            <div class="aviso-nao-pregao">
              ⚠️ A data selecionada <strong>não é um dia de pregão útil B3</strong> (Fim de semana ou Feriado financeiro).
            </div>
          }
        </div>

        <div class="campo" style="margin-top: 16px;">
          <label>Conjuntos de Dados a Consumir:</label>
          <div class="grid-datasets">
            <label class="dataset-opcao">
              <input type="checkbox" [(ngModel)]="datasetBvbg086" />
              <div>
                <strong>BVBG.086 — Preços e Ajustes Diários</strong>
                <span>Cotações e taxas de ajuste de contratos DI1 e futuros da B3.</span>
              </div>
            </label>

            <label class="dataset-opcao">
              <input type="checkbox" [(ngModel)]="datasetBvbg028" />
              <div>
                <strong>BVBG.028 — Cadastro de Instrumentos</strong>
                <span>Tabela cadastral de vencimentos e prazos de futuros.</span>
              </div>
            </label>

            <label class="dataset-opcao">
              <input type="checkbox" [(ngModel)]="datasetPrDi1" />
              <div>
                <strong>Preços de Referência (PR_DI1)</strong>
                <span>Arquivo consolidado de fechamento de derivativos.</span>
              </div>
            </label>

            <label class="dataset-opcao destaque-curva-pronta">
              <input type="checkbox" [(ngModel)]="datasetTaxasRef" />
              <div>
                <strong>Curva Pronta B3 (Taxas de Referência)</strong>
                <span>Curva oficial divulgada vértice a vértice (alimenta curvas IMPORTED).</span>
              </div>
            </label>
          </div>
        </div>

        <div class="card-footer">
          <button
            class="btn btn-primary"
            [disabled]="loading() || !temDatasetMarcado() || !authService.isOperator()"
            [title]="authService.isOperator() ? 'Disparar ingestão na faixa prioritária' : authService.getPermissionExplanation('CURVE_OPERATOR')"
            (click)="disparar()">
            {{ loading() ? 'Disparando...' : '⚡ Disparar Ingestão Prioritária' }}
          </button>
        </div>
      </div>

      <!-- Resposta de Execução -->
      @if (resultadoDisparo(); as r) {
        <div class="resultado-disparo-box" [class.disparo-andamento]="r.status === 'JA_EM_ANDAMENTO'">
          <div class="resultado-header">
            <span class="icone">{{ r.status === 'JA_EM_ANDAMENTO' ? '⏳' : '✅' }}</span>
            <div>
              <h3>{{ r.status === 'JA_EM_ANDAMENTO' ? 'Execução já em andamento para esta data' : 'Ingestão iniciada com sucesso na faixa prioritária' }}</h3>
              <p>{{ r.mensagem || 'Acompanhe o processamento e a construção da curva no Monitor de Execuções.' }}</p>
            </div>
          </div>

          <div class="dados-correlacao">
            <span class="rotulo">Correlation ID:</span>
            <code class="codigo-copia" (click)="copiarCorrelationId(r.correlationId)" title="Clique para copiar">
              {{ r.correlationId }} 📋
            </code>
          </div>

          <div class="acoes-resultado">
            <a [routerLink]="['/execucoes']" [queryParams]="{ correlationId: r.correlationId }" class="btn btn-secondary btn-sm">
              Ver no Monitor de Execuções &rarr;
            </a>
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
    .card-formulario {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
      margin-bottom: var(--space-xl);
    }
    .campo {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .campo label {
      font-size: 13px;
      font-weight: 600;
      color: var(--color-text-secondary);
    }
    .aviso-nao-pregao {
      background-color: rgba(245, 158, 11, 0.15);
      border: 1px solid var(--color-status-aviso);
      color: #fef08a;
      padding: 8px 12px;
      border-radius: var(--radius-sm);
      font-size: 12px;
      margin-top: 6px;
    }
    .grid-datasets {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
      gap: 12px;
      margin-top: 6px;
    }
    .dataset-opcao {
      display: flex;
      gap: 12px;
      padding: 12px;
      background-color: var(--color-bg-page);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-sm);
      cursor: pointer;
    }
    .dataset-opcao:hover {
      border-color: var(--color-border-focus);
    }
    .dataset-opcao div {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .dataset-opcao strong {
      font-size: 13px;
    }
    .dataset-opcao span {
      font-size: 12px;
      color: var(--color-text-muted);
    }
    .destaque-curva-pronta {
      border-color: #0284c7;
      background-color: rgba(2, 132, 199, 0.05);
    }
    .card-footer {
      margin-top: 20px;
      display: flex;
      justify-content: flex-end;
    }
    .resultado-disparo-box {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-status-publicada);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
    }
    .disparo-andamento {
      border-color: var(--color-status-em-risco);
    }
    .resultado-header {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      margin-bottom: 16px;
    }
    .resultado-header .icone {
      font-size: 24px;
    }
    .resultado-header h3 {
      font-size: 16px;
      font-weight: 600;
    }
    .resultado-header p {
      font-size: 13px;
      color: var(--color-text-muted);
      margin-top: 2px;
    }
    .dados-correlacao {
      background-color: var(--color-bg-page);
      padding: 10px 14px;
      border-radius: var(--radius-sm);
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 13px;
      margin-bottom: 16px;
    }
    .codigo-copia {
      font-family: var(--font-mono);
      color: var(--color-border-focus);
      background-color: #1e293b;
      padding: 4px 8px;
      border-radius: 4px;
      cursor: pointer;
    }
    .codigo-copia:hover {
      text-decoration: underline;
    }
    .btn-sm {
      padding: 6px 12px;
      font-size: 13px;
    }
  `]
})
export class DisparoManualComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);

  public dataReferencia: string = new Date().toISOString().substring(0, 10);
  public datasetBvbg086: boolean = true;
  public datasetBvbg028: boolean = true;
  public datasetPrDi1: boolean = true;
  public datasetTaxasRef: boolean = true;

  public avisoFeriado = signal<boolean>(false);
  public loading = signal<boolean>(false);
  public resultadoDisparo = signal<DisparoIngestaoResponse | null>(null);

  ngOnInit(): void {
    this.verificarPregao();
  }

  public verificarPregao(): void {
    const d = new Date(this.dataReferencia + 'T00:00:00');
    const day = d.getDay();
    // 0 = domingo, 6 = sábado
    this.avisoFeriado.set(day === 0 || day === 6);
  }

  public temDatasetMarcado(): boolean {
    return this.datasetBvbg086 || this.datasetBvbg028 || this.datasetPrDi1 || this.datasetTaxasRef;
  }

  public disparar(): void {
    if (!this.authService.isOperator()) return;

    this.loading.set(true);
    const datasets: DatasetB3[] = [];
    if (this.datasetBvbg086) datasets.push('BVBG_086');
    if (this.datasetBvbg028) datasets.push('BVBG_028');
    if (this.datasetPrDi1) datasets.push('PR_DI1');
    if (this.datasetTaxasRef) datasets.push('TAXAS_REFERENCIA');

    this.bffClient.dispararIngestao({
      dataReferencia: this.dataReferencia,
      datasets
    }).subscribe({
      next: (resp) => {
        this.resultadoDisparo.set(resp);
        this.loading.set(false);
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao disparar ingestão.');
        this.loading.set(false);
      }
    });
  }

  public copiarCorrelationId(id: string): void {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(id);
      alert('Correlation ID copiado para a área de transferência!');
    }
  }
}
