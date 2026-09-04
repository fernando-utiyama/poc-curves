import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { ItemExecucaoDTO } from '../../core/api/models.ts';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component.ts';

@Component({
  selector: 'app-execucoes-monitor',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, StatusBadgeComponent],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Monitor de Execuções e Rastreabilidade</h1>
          <p class="page-subtitle">Acompanhamento ponta a ponta do ciclo de vida das execuções e etapas de construção.</p>
        </div>
      </div>

      <!-- Filtros -->
      <div class="filtros-bar">
        <div class="filtro-campo">
          <label>Curva / Alvo:</label>
          <input type="text" [(ngModel)]="filtroCurva" (input)="carregarExecucoes()" placeholder="Ex: PRE, DI1" class="form-input" />
        </div>
        <div class="filtro-campo">
          <label>Data de Referência:</label>
          <input type="date" [(ngModel)]="filtroData" (change)="carregarExecucoes()" class="form-input" />
        </div>
        <div class="filtro-campo">
          <label>Estado:</label>
          <select [(ngModel)]="filtroEstado" (change)="carregarExecucoes()" class="form-select">
            <option value="">Todos</option>
            <option value="CONCLUIDA">Concluída</option>
            <option value="EXECUTANDO">Executando</option>
            <option value="CONSTRUINDO">Construindo</option>
            <option value="EM_RISCO">Em Risco</option>
            <option value="ATRASADA">Atrasada</option>
            <option value="SEM_DADO">Sem Dado</option>
            <option value="FALHOU">Falhou</option>
          </select>
        </div>
        <div class="filtro-campo">
          <label>Correlation ID:</label>
          <input type="text" [(ngModel)]="filtroCorrelationId" (input)="carregarExecucoes()" placeholder="Buscar UUID..." class="form-input" style="width: 220px;" />
        </div>
      </div>

      @if (loading()) {
        <div class="loading-box">Carregando execuções...</div>
      } @else {
        <div class="tabela-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>Disparado em</th>
                <th>Alvo</th>
                <th>Data Ref</th>
                <th>Tipo / Faixa</th>
                <th>Disparado por</th>
                <th>Estado</th>
                <th>Etapa Atual</th>
                <th class="th-num">Duração</th>
                <th>Correlation ID</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              @for (item of itens(); track item.id) {
                <tr [class.linha-sem-dado]="item.estado === 'SEM_DADO'" [class.linha-falha]="item.estado === 'FALHOU'">
                  <td>{{ item.disparadoEm | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td><strong>{{ item.alvo }}</strong></td>
                  <td>{{ item.dataReferencia }}</td>
                  <td>
                    <span class="badge-tipo">{{ item.tipoDisparo }}</span>
                    @if (item.faixa) {
                      <span class="badge-faixa">{{ item.faixa }}</span>
                    }
                  </td>
                  <td>{{ item.disparadoPor || 'Sistema' }}</td>
                  <td>
                    <div class="estado-col">
                      <app-status-badge [status]="item.estado"></app-status-badge>
                      @if (item.estado === 'SEM_DADO' && item.causaFalha) {
                        <span class="motivo-sem-dado">{{ item.causaFalha }}</span>
                      }
                      @if (item.estado === 'FALHOU' && item.causaFalha) {
                        <span class="motivo-falha">{{ item.causaFalha }}</span>
                      }
                    </div>
                  </td>
                  <td>{{ item.etapaAtual || '-' }}</td>
                  <td class="td-num tabular-nums">{{ item.duracaoSegundos !== undefined ? item.duracaoSegundos + 's' : '-' }}</td>
                  <td>
                    <code class="codigo-copia" (click)="copiarCorrelationId(item.correlationId)" title="Clique para copiar">
                      {{ item.correlationId | slice:0:8 }}... 📋
                    </code>
                  </td>
                  <td>
                    @if (item.estado === 'FALHOU' || item.estado === 'ATRASADA' || item.estado === 'EM_RISCO') {
                      <button
                        class="btn btn-secondary btn-xs btn-redisparar"
                        [disabled]="!authService.isOperator()"
                        [title]="authService.isOperator() ? 'Redisparar na faixa prioritária' : authService.getPermissionExplanation('CURVE_OPERATOR')"
                        (click)="redisparar(item.id)">
                        ⚡ Redisparar
                      </button>
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
    .filtros-bar {
      display: flex;
      gap: 16px;
      background-color: var(--color-bg-surface);
      padding: 12px 16px;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      margin-bottom: var(--space-lg);
      flex-wrap: wrap;
    }
    .filtro-campo {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
    }
    .tabela-container {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
    }
    .badge-tipo {
      font-size: 11px;
      background-color: #334155;
      padding: 2px 6px;
      border-radius: 4px;
    }
    .badge-faixa {
      font-size: 10px;
      background-color: #1e3a8a;
      color: #93c5fd;
      padding: 1px 4px;
      border-radius: 3px;
      margin-left: 4px;
    }
    .estado-col {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .motivo-sem-dado {
      font-size: 11px;
      color: #94a3b8;
      font-style: italic;
    }
    .motivo-falha {
      font-size: 11px;
      color: #f87171;
    }
    .codigo-copia {
      font-family: var(--font-mono);
      font-size: 12px;
      color: var(--color-border-focus);
      background-color: #0f172a;
      padding: 2px 6px;
      border-radius: 4px;
      cursor: pointer;
    }
    .codigo-copia:hover {
      text-decoration: underline;
    }
    .btn-xs {
      padding: 3px 8px;
      font-size: 12px;
    }
    .btn-redisparar {
      color: #f59e0b;
      border-color: #f59e0b;
    }
    .linha-sem-dado {
      background-color: rgba(100, 116, 139, 0.05);
    }
    .linha-falha {
      background-color: rgba(239, 68, 68, 0.05);
    }
    .loading-box {
      padding: var(--space-xl);
      text-align: center;
      background-color: var(--color-bg-surface);
      border-radius: var(--radius-md);
    }
  `]
})
export class ExecucoesMonitorComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);
  private route = inject(ActivatedRoute);

  public itens = signal<ItemExecucaoDTO[]>([]);
  public loading = signal<boolean>(false);

  public filtroCurva: string = '';
  public filtroData: string = '';
  public filtroEstado: string = '';
  public filtroCorrelationId: string = '';

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      if (params.get('correlationId')) {
        this.filtroCorrelationId = params.get('correlationId')!;
      }
      this.carregarExecucoes();
    });
  }

  public carregarExecucoes(): void {
    this.loading.set(true);
    this.bffClient.getExecucoes({
      curva: this.filtroCurva || undefined,
      dataReferencia: this.filtroData || undefined,
      estado: this.filtroEstado || undefined,
      correlationId: this.filtroCorrelationId || undefined
    }).subscribe({
      next: (resp) => {
        this.itens.set(resp.itens || []);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  public redisparar(id: string): void {
    if (!this.authService.isOperator()) return;

    this.bffClient.redispararExecucao(id).subscribe({
      next: () => {
        this.carregarExecucoes();
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao redisparar execução.');
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
