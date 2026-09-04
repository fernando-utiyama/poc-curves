import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { ItemPainelDoDiaDTO } from '../../core/api/models.ts';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component.ts';

@Component({
  selector: 'app-painel-do-dia',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, StatusBadgeComponent],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Painel do Dia — Monitoramento de Curvas</h1>
          <p class="page-subtitle">Acompanhe a publicação diária, prazos limites e status de consistência em tempo real.</p>
        </div>
        <div class="filtro-data">
          <label for="dataRef">Data de Referência:</label>
          <input type="date" id="dataRef" [(ngModel)]="dataReferencia" (change)="carregarPainel()" class="form-input" />
          <button class="btn btn-secondary" (click)="carregarPainel()">🔄 Atualizar</button>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-box">Carregando painel do dia...</div>
      } @else if (erro()) {
        <div class="error-box">
          {{ erro() }}
          <button class="btn btn-secondary btn-sm" (click)="carregarPainel()">Tentar Novamente</button>
        </div>
      } @else {
        <div class="grid-resumo">
          <div class="card-metrica">
            <span class="metrica-rotulo">Total de Curvas Ativas</span>
            <span class="metrica-valor">{{ itens().length }}</span>
          </div>
          <div class="card-metrica" [class.destaque-sucesso]="totalPublicadas() > 0">
            <span class="metrica-rotulo">Publicadas</span>
            <span class="metrica-valor">{{ totalPublicadas() }}</span>
          </div>
          <div class="card-metrica" [class.destaque-risco]="totalEmRisco() > 0">
            <span class="metrica-rotulo">Em Risco de Corte</span>
            <span class="metrica-valor">{{ totalEmRisco() }}</span>
          </div>
          <div class="card-metrica" [class.destaque-atraso]="totalAtrasadas() > 0">
            <span class="metrica-rotulo">Atrasadas / Reprovadas</span>
            <span class="metrica-valor">{{ totalAtrasadas() + totalReprovadas() }}</span>
          </div>
        </div>

        <div class="tabela-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>Código / Nome</th>
                <th>Origem</th>
                <th>Status da Publicação</th>
                <th>Horário Limite</th>
                <th>Tempo Restante / Margem</th>
                <th>Etapa Atual</th>
                <th>Versão Vigente</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              @for (item of itens(); track item.codigoCurva) {
                <tr [class.linha-risco]="item.estado === 'EM_RISCO'" [class.linha-atrasada]="item.estado === 'ATRASADA'" [class.linha-reprovada]="item.estado === 'REPROVADA'">
                  <td>
                    <div class="curva-info">
                      <a [routerLink]="['/curvas', item.codigoCurva]" [queryParams]="{ dataReferencia: dataReferencia }" class="curva-codigo">
                        {{ item.codigoCurva }}
                      </a>
                      <span class="curva-nome">{{ item.nomeCurva }}</span>
                    </div>
                  </td>
                  <td>
                    <span class="modo-origem-badge" [class.modo-bootstrapped]="item.modoOrigem === 'BOOTSTRAPPED'">
                      {{ item.modoOrigem === 'BOOTSTRAPPED' ? 'Construída' : 'Importada' }}
                    </span>
                    @if (item.origemPublicacao === 'CARREGADA') {
                      <span class="origem-carregada-badge" title="Publicada via carga manual de contingência">CARREGADA</span>
                    }
                  </td>
                  <td>
                    <div class="status-col">
                      <app-status-badge [status]="item.estado"></app-status-badge>
                      @if (item.estado === 'PUBLICADA_COM_AVISO' && item.avisosValidacao?.length) {
                        <span class="aviso-txt" title="Avisos registrados">⚠️ {{ item.avisosValidacao?.length }} aviso(s)</span>
                      }
                      @if (item.estado === 'REPROVADA' && item.testesReprovados?.length) {
                        <span class="reprovado-txt" title="Testes reprovados no gate">❌ {{ item.testesReprovados?.join(', ') }}</span>
                      }
                      @if (item.estado === 'NAO_INICIADA' && item.horarioPrevisto) {
                        <span class="horario-previsto">Previsto: {{ item.horarioPrevisto }}</span>
                      }
                    </div>
                  </td>
                  <td class="tabular-nums">{{ item.horarioLimite }}</td>
                  <td class="tabular-nums" [class.tempo-negativo]="item.estado === 'ATRASADA'" [class.tempo-alerta]="item.estado === 'EM_RISCO'">
                    {{ item.tempoRestanteOuMargem || '-' }}
                  </td>
                  <td>{{ item.etapaAtual || '-' }}</td>
                  <td class="tabular-nums">v{{ item.versaoVigente || 1 }}</td>
                  <td>
                    <div class="acoes-linha">
                      <a [routerLink]="['/curvas', item.codigoCurva]" [queryParams]="{ dataReferencia: dataReferencia }" class="btn btn-secondary btn-xs">
                        Visualizar
                      </a>
                      @if (item.estado === 'ATRASADA' || item.estado === 'EM_RISCO' || item.estado === 'REPROVADA') {
                        <button
                          class="btn btn-secondary btn-xs btn-redisparar"
                          [disabled]="!authService.isOperator()"
                          [title]="authService.isOperator() ? 'Redisparar ingestão prioritária' : authService.getPermissionExplanation('CURVE_OPERATOR')"
                          (click)="redisparar(item)">
                          ⚡ Redisparar
                        </button>
                      }
                    </div>
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
      max-width: 1400px;
      margin: 0 auto;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: var(--space-xl);
      flex-wrap: wrap;
      gap: var(--space-md);
    }
    .page-title {
      font-size: 22px;
      font-weight: 700;
      color: var(--color-text-primary);
    }
    .page-subtitle {
      font-size: 13px;
      color: var(--color-text-muted);
      margin-top: 2px;
    }
    .filtro-data {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
      font-size: 13px;
    }
    .grid-resumo {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: var(--space-md);
      margin-bottom: var(--space-xl);
    }
    .card-metrica {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-md) var(--space-lg);
      display: flex;
      flex-direction: column;
    }
    .metrica-rotulo {
      font-size: 12px;
      color: var(--color-text-muted);
      text-transform: uppercase;
      font-weight: 600;
      letter-spacing: 0.04em;
    }
    .metrica-valor {
      font-size: 26px;
      font-weight: 700;
      font-family: var(--font-mono);
      margin-top: 4px;
    }
    .destaque-sucesso .metrica-valor { color: var(--color-status-publicada); }
    .destaque-risco .metrica-valor { color: var(--color-status-em-risco); }
    .destaque-atraso .metrica-valor { color: var(--color-status-atrasada); }

    .tabela-container {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      overflow-x: auto;
    }
    .curva-info {
      display: flex;
      flex-direction: column;
    }
    .curva-codigo {
      font-weight: 600;
      color: var(--color-border-focus);
      text-decoration: none;
    }
    .curva-codigo:hover {
      text-decoration: underline;
    }
    .curva-nome {
      font-size: 11px;
      color: var(--color-text-muted);
    }
    .modo-origem-badge {
      font-size: 11px;
      padding: 2px 6px;
      border-radius: 4px;
      background-color: #334155;
      color: #cbd5e1;
    }
    .modo-bootstrapped {
      background-color: #1e3a8a;
      color: #93c5fd;
    }
    .origem-carregada-badge {
      font-size: 10px;
      background-color: #78350f;
      color: #fde68a;
      padding: 1px 4px;
      border-radius: 3px;
      margin-left: 4px;
      font-weight: 600;
    }
    .status-col {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .aviso-txt {
      font-size: 11px;
      color: var(--color-status-aviso);
    }
    .reprovado-txt {
      font-size: 11px;
      color: var(--color-status-reprovada);
    }
    .horario-previsto {
      font-size: 11px;
      color: var(--color-text-muted);
    }
    .tempo-negativo {
      color: var(--color-status-atrasada);
      font-weight: 600;
    }
    .tempo-alerta {
      color: var(--color-status-em-risco);
      font-weight: 600;
    }
    .acoes-linha {
      display: flex;
      gap: 6px;
    }
    .btn-xs {
      padding: 3px 8px;
      font-size: 12px;
    }
    .btn-redisparar {
      color: #f59e0b;
      border-color: #f59e0b;
    }
    .linha-risco {
      background-color: rgba(249, 115, 22, 0.05);
    }
    .linha-atrasada {
      background-color: rgba(239, 68, 68, 0.05);
    }
    .linha-reprovada {
      background-color: rgba(220, 38, 38, 0.05);
    }
    .loading-box, .error-box {
      padding: var(--space-xl);
      text-align: center;
      background-color: var(--color-bg-surface);
      border-radius: var(--radius-md);
    }
  `]
})
export class PainelDoDiaComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);

  public dataReferencia: string = new Date().toISOString().substring(0, 10);
  public itens = signal<ItemPainelDoDiaDTO[]>([]);
  public loading = signal<boolean>(false);
  public erro = signal<string | null>(null);

  public totalPublicadas = signal<number>(0);
  public totalEmRisco = signal<number>(0);
  public totalAtrasadas = signal<number>(0);
  public totalReprovadas = signal<number>(0);

  ngOnInit(): void {
    this.carregarPainel();
  }

  public carregarPainel(): void {
    this.loading.set(true);
    this.erro.set(null);

    this.bffClient.getPainelDoDia(this.dataReferencia).subscribe({
      next: (resp) => {
        this.itens.set(resp.itens || []);
        this.atualizarContadores(resp.itens || []);
        this.loading.set(false);
      },
      error: (err) => {
        this.erro.set(err.error?.mensagem || 'Falha ao carregar dados do painel do dia.');
        this.loading.set(false);
      }
    });
  }

  private atualizarContadores(lista: ItemPainelDoDiaDTO[]): void {
    this.totalPublicadas.set(lista.filter(i => i.estado === 'PUBLICADA' || i.estado === 'PUBLICADA_COM_AVISO').length);
    this.totalEmRisco.set(lista.filter(i => i.estado === 'EM_RISCO').length);
    this.totalAtrasadas.set(lista.filter(i => i.estado === 'ATRASADA').length);
    this.totalReprovadas.set(lista.filter(i => i.estado === 'REPROVADA').length);
  }

  public redisparar(item: ItemPainelDoDiaDTO): void {
    if (!this.authService.isOperator()) return;

    this.bffClient.dispararIngestao({
      dataReferencia: this.dataReferencia,
      datasets: item.modoOrigem === 'BOOTSTRAPPED' ? ['BVBG_086', 'BVBG_028'] : ['TAXAS_REFERENCIA']
    }).subscribe({
      next: () => {
        this.carregarPainel();
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao acionar redisparo prioritário.');
      }
    });
  }
}
