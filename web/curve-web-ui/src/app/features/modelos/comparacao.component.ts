import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { ComparacaoResponse } from '../../core/api/models.ts';
import { TaxaFormatPipe, BpsFormatPipe, FatorDescontoPipe } from '../../shared/pipes/taxa-format.pipe.ts';

@Component({
  selector: 'app-comparacao',
  standalone: true,
  imports: [CommonModule, FormsModule, TaxaFormatPipe, BpsFormatPipe, FatorDescontoPipe],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Comparação de Curvas e Modelos</h1>
          <p class="page-subtitle">Compare vértice a vértice a curva construída internamente contra a taxa de referência oficial da B3 ou entre dois modelos de cálculo.</p>
        </div>
      </div>

      <!-- Painel de Parâmetros -->
      <div class="card-parametros">
        <div class="grid-form-3">
          <div class="campo">
            <label>Data de Referência:</label>
            <input type="date" [(ngModel)]="dataReferencia" class="form-input" />
          </div>
          <div class="campo">
            <label>Curva Base (A):</label>
            <input type="text" [(ngModel)]="curvaACodigo" class="form-input" placeholder="Ex: PRE" />
          </div>
          <div class="campo">
            <label>Curva Comparada (B):</label>
            <input type="text" [(ngModel)]="curvaBCodigo" class="form-input" placeholder="Ex: PRE_B3 (Importada)" />
          </div>
        </div>

        <div class="card-acoes">
          <button class="btn btn-primary" [disabled]="loading()" (click)="comparar()">
            {{ loading() ? 'Comparando...' : '⚖️ Executar Comparação' }}
          </button>
        </div>
      </div>

      @if (resultado(); as r) {
        <div class="resultado-box">
          <div class="resultado-header">
            <div>
              <h2 class="resultado-titulo">Diferenças Ponto a Ponto ({{ r.diferencas.length }} prazos)</h2>
              <span class="resultado-sub">{{ r.rotuloCurvaA || 'Curva A' }} vs {{ r.rotuloCurvaB || 'Curva B' }} ({{ r.dataReferencia }})</span>
            </div>
            <span class="dica">Prazos sem contraparte são sinalizados explicitamente</span>
          </div>

          <div class="tabela-scroll">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="th-num">Prazo (DU)</th>
                  <th class="th-num">Taxa A (% a.a.)</th>
                  <th class="th-num">Taxa B (% a.a.)</th>
                  <th class="th-num">Diferença Spread</th>
                  <th class="th-num">Fator Desc A</th>
                  <th class="th-num">Fator Desc B</th>
                  <th>Status do Ponto</th>
                </tr>
              </thead>
              <tbody>
                @for (d of r.diferencas; track d.prazoDiasUteis) {
                  <tr [class.linha-divergente]="d.status === 'COINCIDENTE' && d.diferencaTaxaBps && d.diferencaTaxaBps !== '0'" [class.linha-parcial]="d.status !== 'COINCIDENTE'">
                    <td class="td-num tabular-nums"><strong>{{ d.prazoDiasUteis }}</strong></td>
                    <td class="td-num tabular-nums">{{ d.taxaA ? (d.taxaA | taxaFormat) : '-' }}</td>
                    <td class="td-num tabular-nums">{{ d.taxaB ? (d.taxaB | taxaFormat) : '-' }}</td>
                    <td class="td-num tabular-nums spread-col">
                      @if (d.diferencaTaxaBps) {
                        <strong>{{ d.diferencaTaxaBps | bpsFormat }}</strong>
                      } @else { - }
                    </td>
                    <td class="td-num tabular-nums">{{ d.fatorDescontoA ? (d.fatorDescontoA | fatorDesconto) : '-' }}</td>
                    <td class="td-num tabular-nums">{{ d.fatorDescontoB ? (d.fatorDescontoB | fatorDesconto) : '-' }}</td>
                    <td>
                      @if (d.status === 'COINCIDENTE') {
                        <span class="badge-status badge-coincidente">Ambas Presentes</span>
                      } @else if (d.status === 'PRESENTE_APENAS_EM_A') {
                        <span class="badge-status badge-so-a">Apenas na Curva A</span>
                      } @else if (d.status === 'PRESENTE_APENAS_EM_B') {
                        <span class="badge-status badge-so-b">Apenas na Curva B</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-container {
      padding: var(--space-xl);
      max-width: 1400px;
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
    .card-parametros {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
      margin-bottom: var(--space-xl);
    }
    .grid-form-3 {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 16px;
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
      font-size: 16px;
      font-weight: 600;
    }
    .resultado-sub {
      font-size: 12px;
      color: var(--color-text-muted);
    }
    .dica {
      font-size: 11px;
      color: var(--color-text-muted);
    }
    .tabela-scroll {
      max-height: 600px;
      overflow-y: auto;
    }
    .spread-col {
      color: #38bdf8;
    }
    .badge-status {
      font-size: 10px;
      padding: 2px 6px;
      border-radius: 3px;
      font-weight: 600;
    }
    .badge-coincidente { background-color: rgba(16, 185, 129, 0.15); color: #10b981; }
    .badge-so-a { background-color: rgba(59, 130, 246, 0.15); color: #3b82f6; }
    .badge-so-b { background-color: rgba(245, 158, 11, 0.15); color: #f59e0b; }
    .linha-parcial {
      background-color: rgba(100, 116, 139, 0.05);
    }
    .linha-divergente {
      background-color: rgba(2, 132, 199, 0.03);
    }
  `]
})
export class ComparacaoComponent implements OnInit {
  private bffClient = inject(CurveBffClientService);

  public dataReferencia: string = new Date().toISOString().substring(0, 10);
  public curvaACodigo: string = 'PRE';
  public curvaBCodigo: string = 'PRE_B3_OFICIAL';

  public loading = signal<boolean>(false);
  public resultado = signal<ComparacaoResponse | null>(null);

  ngOnInit(): void {}

  public comparar(): void {
    this.loading.set(true);
    this.bffClient.compararCurvas({
      dataReferencia: this.dataReferencia,
      curvaA: { codigo: this.curvaACodigo },
      curvaB: { codigo: this.curvaBCodigo }
    }).subscribe({
      next: (resp) => {
        this.resultado.set(resp);
        this.loading.set(false);
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao comparar curvas.');
        this.loading.set(false);
      }
    });
  }
}
