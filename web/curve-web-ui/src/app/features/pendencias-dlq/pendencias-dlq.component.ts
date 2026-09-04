import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { GrupoPendenciaDlqDTO, ItemPendenciaDlqDTO } from '../../core/api/models.ts';

@Component({
  selector: 'app-pendencias-dlq',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Gestão de Mensagens em Dead-Letter (DLQ)</h1>
          <p class="page-subtitle">Falhas de ingestão e desserialização isoladas e agrupadas para reprocessamento ou descarte auditado.</p>
        </div>
        <button class="btn btn-secondary" (click)="carregarGrupos()">🔄 Atualizar</button>
      </div>

      @if (loading()) {
        <div class="loading-box">Carregando pendências...</div>
      } @else if (grupos().length === 0) {
        <div class="sem-pendencias-box">
          <span class="icone">✨</span>
          <h2>Nenhuma mensagem em Dead-Letter</h2>
          <p>Todas as partições estão limpas e operando normalmente sem mensagens presas.</p>
        </div>
      } @else {
        <div class="lista-grupos">
          @for (g of grupos(); track g.grupoId) {
            <div class="card-grupo" [class.grupo-reprocessando]="g.estado === 'EM_REPROCESSAMENTO'">
              <div class="grupo-header">
                <div class="grupo-info">
                  <div class="grupo-titulo">
                    <span class="badge-motivo">{{ g.motivo }}</span>
                    <strong>{{ g.fonte }} / {{ g.conjuntoDados }}</strong>
                    <span class="data-ref">Ref: {{ g.dataReferencia }}</span>
                  </div>
                  <div class="grupo-meta">
                    <span><strong>{{ g.totalMensagens }}</strong> mensagens agrupadas</span>
                    <span>Primeira falha: {{ g.primeiraFalhaEm | date:'dd/MM/yyyy HH:mm:ss' }}</span>
                    <span>Última falha: {{ g.ultimaFalhaEm | date:'dd/MM/yyyy HH:mm:ss' }}</span>
                  </div>
                </div>

                <div class="grupo-acoes">
                  <button class="btn btn-secondary btn-sm" (click)="toggleExpandir(g)">
                    {{ grupoExpandido() === g.grupoId ? 'Ocultar Detalhes' : 'Expandir Mensagens (' + g.totalMensagens + ')' }}
                  </button>

                  <button
                    class="btn btn-primary btn-sm"
                    [disabled]="g.estado === 'EM_REPROCESSAMENTO' || !authService.isOperator()"
                    [title]="authService.isOperator() ? 'Republicar mensagens do grupo na fila original' : authService.getPermissionExplanation('CURVE_OPERATOR')"
                    (click)="reprocessarGrupo(g)">
                    ⚡ Reprocessar Grupo
                  </button>

                  <button
                    class="btn btn-secondary btn-sm btn-descarte"
                    [disabled]="!authService.isOperator()"
                    [title]="authService.isOperator() ? 'Descartar com justificativa auditada' : authService.getPermissionExplanation('CURVE_OPERATOR')"
                    (click)="abrirModalDescarte(g.grupoId)">
                    🗑️ Descartar
                  </button>
                </div>
              </div>

              @if (g.detalheRepresentativo) {
                <div class="detalhe-amostra">
                  <small>Exemplo do erro:</small>
                  <code>{{ g.detalheRepresentativo }}</code>
                </div>
              }

              <!-- Detalhamento Individual do Grupo Expandido -->
              @if (grupoExpandido() === g.grupoId) {
                <div class="detalhes-tabela">
                  @if (carregandoDetalhes()) {
                    <div class="loading-box">Carregando mensagens do grupo...</div>
                  } @else {
                    <table class="data-table">
                      <thead>
                        <tr>
                          <th>Data/Hora Falha</th>
                          <th>Tópico / Partição / Offset</th>
                          <th>Correlation ID</th>
                          <th>Tentativas</th>
                          <th>Ações</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (item of itensGrupo(); track item.id) {
                          <tr>
                            <td>{{ item.falhouEm | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                            <td><code>{{ item.topicoOrigem }}:{{ item.particao }}{{ '@' }}{{ item.offset }}</code></td>
                            <td>
                              <a [routerLink]="['/execucoes']" [queryParams]="{ correlationId: item.correlationId }" class="link-corr">
                                {{ item.correlationId | slice:0:8 }}... 🔗
                              </a>
                            </td>
                            <td>{{ item.tentativas || 1 }}</td>
                            <td>
                              <div class="acoes-item">
                                <button
                                  class="btn btn-secondary btn-xs"
                                  [disabled]="!authService.isOperator()"
                                  (click)="reprocessarItem(item.id)">
                                  ⚡ Reprocessar
                                </button>
                                <button
                                  class="btn btn-secondary btn-xs btn-descarte"
                                  [disabled]="!authService.isOperator()"
                                  (click)="abrirModalDescarte(g.grupoId, item.id)">
                                  Descartar
                                </button>
                              </div>
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  }
                </div>
              }
            </div>
          }
        </div>
      }

      <!-- Modal de Descarte com Justificativa Obrigatória -->
      @if (modalDescarteAberto()) {
        <div class="modal-backdrop">
          <div class="modal-dialog">
            <h3 class="modal-title">Descarte de Mensagem em Dead-Letter</h3>
            <p class="modal-descricao">
              O descarte é definitivo e audita quem executou e o motivo da baixa.
              O preenchimento de justificativa é <strong>obrigatório</strong>.
            </p>

            <div class="campo" style="margin: 16px 0;">
              <label>Justificativa do Descarte *:</label>
              <textarea [(ngModel)]="justificativaDescarte" class="form-textarea" rows="3" placeholder="Ex: Arquivo corrigido e reprocessado pelo operador em lote posterior."></textarea>
            </div>

            <div class="modal-acoes">
              <button class="btn btn-secondary" (click)="fecharModalDescarte()">Cancelar</button>
              <button
                class="btn btn-primary btn-descarte"
                [disabled]="!justificativaDescarte.trim() || descartando()"
                (click)="confirmarDescarte()">
                {{ descartando() ? 'Descartando...' : 'Confirmar Descarte' }}
              </button>
            </div>
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
      display: flex;
      justify-content: space-between;
      align-items: center;
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
    .lista-grupos {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .card-grupo {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
    }
    .grupo-reprocessando {
      border-color: var(--color-primary);
      background-color: rgba(2, 132, 199, 0.05);
    }
    .grupo-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;
    }
    .grupo-titulo {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 15px;
      margin-bottom: 6px;
    }
    .badge-motivo {
      background-color: rgba(239, 68, 68, 0.2);
      color: #ef4444;
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 4px;
    }
    .data-ref {
      font-size: 12px;
      color: var(--color-text-muted);
    }
    .grupo-meta {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: var(--color-text-muted);
    }
    .grupo-acoes {
      display: flex;
      gap: 8px;
    }
    .detalhe-amostra {
      margin-top: 12px;
      background-color: var(--color-bg-page);
      padding: 8px 12px;
      border-radius: var(--radius-sm);
      font-size: 12px;
    }
    .detalhe-amostra small {
      display: block;
      color: var(--color-text-muted);
      margin-bottom: 2px;
    }
    .detalhe-amostra code {
      color: #f87171;
    }
    .detalhes-tabela {
      margin-top: 16px;
      border-top: 1px solid var(--color-border);
      padding-top: 12px;
    }
    .link-corr {
      color: var(--color-border-focus);
      text-decoration: none;
    }
    .btn-descarte {
      color: #ef4444;
      border-color: #ef4444;
    }
    .acoes-item {
      display: flex;
      gap: 4px;
    }
    .btn-xs {
      padding: 2px 6px;
      font-size: 11px;
    }
    .sem-pendencias-box {
      text-align: center;
      padding: 60px 24px;
      background-color: var(--color-bg-surface);
      border-radius: var(--radius-md);
    }
    .sem-pendencias-box .icone {
      font-size: 40px;
      margin-bottom: 12px;
      display: block;
    }
    .sem-pendencias-box h2 {
      font-size: 18px;
      margin-bottom: 6px;
      color: var(--color-status-publicada);
    }
    .sem-pendencias-box p {
      color: var(--color-text-muted);
    }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background-color: rgba(0, 0, 0, 0.7);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }
    .modal-dialog {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      padding: 24px;
      max-width: 500px;
      width: 100%;
    }
    .modal-title {
      font-size: 17px;
      font-weight: 600;
      margin-bottom: 6px;
    }
    .modal-descricao {
      font-size: 13px;
      color: var(--color-text-muted);
    }
    .form-textarea {
      width: 100%;
      box-sizing: border-box;
    }
    .modal-acoes {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
    }
    .loading-box {
      padding: var(--space-xl);
      text-align: center;
      background-color: var(--color-bg-surface);
      border-radius: var(--radius-md);
    }
  `]
})
export class PendenciasDlqComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);

  public grupos = signal<GrupoPendenciaDlqDTO[]>([]);
  public loading = signal<boolean>(false);
  public grupoExpandido = signal<string | null>(null);
  public itensGrupo = signal<ItemPendenciaDlqDTO[]>([]);
  public carregandoDetalhes = signal<boolean>(false);

  public modalDescarteAberto = signal<boolean>(false);
  public descarteGrupoId: string | null = null;
  public descartePendenciaId: string | null = null;
  public justificativaDescarte: string = '';
  public descartando = signal<boolean>(false);

  ngOnInit(): void {
    this.carregarGrupos();
  }

  public carregarGrupos(): void {
    this.loading.set(true);
    this.bffClient.getPendenciasDlqGrupos().subscribe({
      next: (resp) => {
        this.grupos.set(resp.grupos || []);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  public toggleExpandir(g: GrupoPendenciaDlqDTO): void {
    if (this.grupoExpandido() === g.grupoId) {
      this.grupoExpandido.set(null);
      this.itensGrupo.set([]);
    } else {
      this.grupoExpandido.set(g.grupoId);
      this.carregarDetalhes(g.grupoId);
    }
  }

  private carregarDetalhes(grupoId: string): void {
    this.carregandoDetalhes.set(true);
    this.bffClient.getPendenciasDlqDetalhes(grupoId).subscribe({
      next: (resp) => {
        this.itensGrupo.set(resp.itens || []);
        this.carregandoDetalhes.set(false);
      },
      error: () => {
        this.carregandoDetalhes.set(false);
      }
    });
  }

  public reprocessarGrupo(g: GrupoPendenciaDlqDTO): void {
    if (!this.authService.isOperator()) return;

    this.bffClient.reprocessarPendenciasDlq({ grupoId: g.grupoId }).subscribe({
      next: () => {
        this.carregarGrupos();
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao reprocessar grupo.');
      }
    });
  }

  public reprocessarItem(id: string): void {
    if (!this.authService.isOperator()) return;

    this.bffClient.reprocessarPendenciasDlq({ pendenciaId: id }).subscribe({
      next: () => {
        if (this.grupoExpandido()) {
          this.carregarDetalhes(this.grupoExpandido()!);
        }
        this.carregarGrupos();
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao reprocessar mensagem.');
      }
    });
  }

  public abrirModalDescarte(grupoId: string, pendenciaId?: string): void {
    if (!this.authService.isOperator()) return;

    this.descarteGrupoId = grupoId;
    this.descartePendenciaId = pendenciaId || null;
    this.justificativaDescarte = '';
    this.modalDescarteAberto.set(true);
  }

  public fecharModalDescarte(): void {
    this.modalDescarteAberto.set(false);
    this.descarteGrupoId = null;
    this.descartePendenciaId = null;
  }

  public confirmarDescarte(): void {
    if (!this.justificativaDescarte.trim()) return;

    this.descartando.set(true);
    this.bffClient.descartarPendenciasDlq({
      justificativa: this.justificativaDescarte,
      grupoId: this.descarteGrupoId || undefined,
      pendenciaId: this.descartePendenciaId || undefined
    }).subscribe({
      next: () => {
        this.descartando.set(false);
        this.fecharModalDescarte();
        this.carregarGrupos();
      },
      error: (err) => {
        alert(err.error?.mensagem || 'Falha ao descartar pendência.');
        this.descartando.set(false);
      }
    });
  }
}
