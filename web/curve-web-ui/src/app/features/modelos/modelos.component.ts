import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { ModeloDTO } from '../../core/api/models.ts';

@Component({
  selector: 'app-modelos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Catálogo de Modelos de Cálculo (Bootstrap)</h1>
          <p class="page-subtitle">Gerencie modelos embutidos (Java) e importe scripts Groovy dinâmicos sem redeploy do motor.</p>
        </div>
        <div class="header-acoes">
          <a routerLink="/comparacao" class="btn btn-secondary">⚖️ Comparar Modelos / Curvas</a>
        </div>
      </div>

      <div class="layout-modelos">
        <!-- Lista de Modelos -->
        <div class="painel-modelos">
          <h2 class="secao-titulo">Modelos Registrados</h2>
          @if (loading()) {
            <div class="loading-box">Carregando modelos...</div>
          } @else {
            <div class="cards-grid">
              @for (m of modelos(); track m.id) {
                <div class="card-modelo" [class.card-builtin]="m.tipo === 'BUILTIN'">
                  <div class="modelo-topo">
                    <div class="modelo-tit">
                      <strong>{{ m.nome }}</strong>
                      <span class="badge-tipo" [class.badge-groovy]="m.tipo === 'GROOVY'">{{ m.tipo }}</span>
                    </div>
                    <span class="badge-estado" [class.badge-ativo]="m.estado === 'ATIVO'">{{ m.estado }}</span>
                  </div>
                  <p class="modelo-desc">{{ m.descricao || 'Modelo de cálculo de estrutura a termo.' }}</p>
                  <div class="modelo-meta">
                    <span>Checksum: <code>{{ m.checksum | slice:0:10 }}...</code></span>
                    @if (m.autor) { <span>Autor: {{ m.autor }}</span> }
                  </div>
                </div>
              }
            </div>
          }
        </div>

        <!-- Importar Modelo Groovy -->
        <div class="painel-importar">
          <h2 class="secao-titulo">Importar Novo Modelo Groovy (Admin)</h2>
          <div class="form-importar">
            <div class="campo">
              <label>Identificador / Nome do Modelo *</label>
              <input type="text" [(ngModel)]="novoNome" class="form-input" placeholder="Ex: GROOVY_SPLINE_CUSTOM" />
            </div>
            <div class="campo">
              <label>Descrição *</label>
              <input type="text" [(ngModel)]="novaDescricao" class="form-input" placeholder="Ex: Modelo com nós intermediários de DI1" />
            </div>
            <div class="campo">
              <label>Script Groovy (Contido e Seguro) *</label>
              <textarea [(ngModel)]="scriptConteudo" class="form-textarea script-area" rows="8" placeholder="// Script Groovy puro que recebe insumos e retorna vértices"></textarea>
            </div>

            @if (erroCompilacao()) {
              <div class="erro-compilacao">
                <strong>Falha na Validação/Compilação:</strong>
                <p>{{ erroCompilacao() }}</p>
              </div>
            }

            <button
              class="btn btn-primary"
              [disabled]="importando() || !novoNome.trim() || !scriptConteudo.trim() || !authService.isAdmin()"
              [title]="authService.isAdmin() ? 'Compilar e registrar modelo Groovy' : authService.getPermissionExplanation('CURVE_ADMIN')"
              (click)="importarGroovy()">
              {{ importando() ? 'Validando e Compilando...' : '📥 Compilar e Registrar Modelo' }}
            </button>
          </div>
        </div>
      </div>
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
    .layout-modelos {
      display: grid;
      grid-template-columns: 1fr 450px;
      gap: var(--space-lg);
    }
    @media (max-width: 1000px) {
      .layout-modelos { grid-template-columns: 1fr; }
    }
    .painel-modelos, .painel-importar {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
    }
    .secao-titulo {
      font-size: 15px;
      font-weight: 600;
      margin-bottom: var(--space-md);
      border-bottom: 1px solid var(--color-border);
      padding-bottom: 6px;
    }
    .cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 12px;
    }
    .card-modelo {
      background-color: var(--color-bg-page);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-sm);
      padding: 12px;
    }
    .card-builtin {
      border-left: 3px solid #38bdf8;
    }
    .modelo-topo {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;
    }
    .modelo-tit {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .badge-tipo {
      font-size: 10px;
      background-color: #334155;
      padding: 1px 5px;
      border-radius: 3px;
      font-weight: 600;
    }
    .badge-groovy {
      background-color: #701a75;
      color: #f5d0fe;
    }
    .badge-estado {
      font-size: 10px;
      padding: 1px 5px;
      border-radius: 3px;
    }
    .badge-ativo {
      background-color: rgba(16, 185, 129, 0.2);
      color: #10b981;
    }
    .modelo-desc {
      font-size: 12px;
      color: var(--color-text-secondary);
      margin-bottom: 8px;
    }
    .modelo-meta {
      font-size: 11px;
      color: var(--color-text-muted);
      display: flex;
      justify-content: space-between;
    }
    .form-importar {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .campo {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .campo label {
      font-size: 12px;
      font-weight: 600;
      color: var(--color-text-secondary);
    }
    .script-area {
      font-family: var(--font-mono);
      font-size: 12px;
    }
    .erro-compilacao {
      background-color: rgba(220, 38, 38, 0.15);
      border: 1px solid var(--color-status-reprovada);
      color: #fca5a5;
      padding: 8px 12px;
      border-radius: var(--radius-sm);
      font-size: 12px;
    }
  `]
})
export class ModelosComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);

  public modelos = signal<ModeloDTO[]>([]);
  public loading = signal<boolean>(false);
  public importando = signal<boolean>(false);
  public erroCompilacao = signal<string | null>(null);

  public novoNome: string = '';
  public novaDescricao: string = '';
  public scriptConteudo: string = '';

  ngOnInit(): void {
    this.carregarModelos();
  }

  public carregarModelos(): void {
    this.loading.set(true);
    this.bffClient.getModelos().subscribe({
      next: (resp) => {
        this.modelos.set(resp.modelos || []);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  public importarGroovy(): void {
    if (!this.authService.isAdmin()) return;

    this.importando.set(true);
    this.erroCompilacao.set(null);

    this.bffClient.importarModeloGroovy({
      nome: this.novoNome,
      descricao: this.novaDescricao,
      scriptConteudo: this.scriptConteudo
    }).subscribe({
      next: () => {
        this.novoNome = '';
        this.novaDescricao = '';
        this.scriptConteudo = '';
        this.importando.set(false);
        this.carregarModelos();
      },
      error: (err) => {
        this.erroCompilacao.set(err.error?.mensagem || 'Erro ao compilar e validar script Groovy.');
        this.importando.set(false);
      }
    });
  }
}
