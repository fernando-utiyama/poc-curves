import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { ItemCatalogoDTO } from '../../core/api/models.ts';

@Component({
  selector: 'app-catalogo',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Catálogo de Definições de Curva</h1>
          <p class="page-subtitle">Curvas cadastradas, convenções financeiras, modelos de cálculo e versões vigentes.</p>
        </div>
        <div class="header-actions">
          <a
            [routerLink]="['/catalogo/nova']"
            class="btn btn-primary"
            [class.btn-disabled]="!authService.isAdmin()"
            [title]="authService.isAdmin() ? 'Cadastrar nova definição de curva' : authService.getPermissionExplanation('CURVE_ADMIN')">
            ➕ Nova Curva
          </a>
        </div>
      </div>

      <!-- Filtros -->
      <div class="filtros-bar">
        <div class="filtro-campo">
          <label>Código:</label>
          <input type="text" [(ngModel)]="filtroCodigo" (input)="carregarCatalogo()" placeholder="Ex: PRE, DOL..." class="form-input" />
        </div>
        <div class="filtro-campo">
          <label>Modo de Origem:</label>
          <select [(ngModel)]="filtroModoOrigem" (change)="carregarCatalogo()" class="form-select">
            <option value="">Todos</option>
            <option value="BOOTSTRAPPED">Construída (BOOTSTRAPPED)</option>
            <option value="IMPORTED">Importada (IMPORTED)</option>
          </select>
        </div>
        <div class="filtro-campo">
          <label>Estado:</label>
          <select [(ngModel)]="filtroEstado" (change)="carregarCatalogo()" class="form-select">
            <option value="">Todos</option>
            <option value="ATIVA">Ativa</option>
            <option value="RASCUNHO">Rascunho</option>
            <option value="APOSENTADA">Aposentada</option>
          </select>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-box">Carregando catálogo...</div>
      } @else {
        <div class="tabela-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Nome da Curva</th>
                <th>Moeda</th>
                <th>Modo de Origem</th>
                <th>Estado</th>
                <th>Versão Vigente</th>
                <th>Modelo Apontado</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              @for (item of itens(); track item.codigo) {
                <tr>
                  <td>
                    <a [routerLink]="['/curvas', item.codigo]" class="codigo-link">{{ item.codigo }}</a>
                  </td>
                  <td>{{ item.nome }}</td>
                  <td>{{ item.moeda }}</td>
                  <td>
                    <span class="badge-origem" [class.badge-bootstrapped]="item.modoOrigem === 'BOOTSTRAPPED'">
                      {{ item.modoOrigem === 'BOOTSTRAPPED' ? 'Construída (DI1)' : 'Importada (B3)' }}
                    </span>
                  </td>
                  <td>
                    <span class="badge-estado" [ngClass]="'estado-' + item.estado.toLowerCase()">
                      {{ item.estado }}
                    </span>
                  </td>
                  <td class="tabular-nums">v{{ item.versaoVigente }}</td>
                  <td><code>{{ item.modeloApontado || 'Padrão Embutido' }}</code></td>
                  <td>
                    <div class="acoes">
                      <a [routerLink]="['/curvas', item.codigo]" class="btn btn-secondary btn-xs">Visualizar</a>
                      <a [routerLink]="['/catalogo', item.codigo, 'editar']" class="btn btn-secondary btn-xs">Editar Definição</a>
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
    .codigo-link {
      color: var(--color-border-focus);
      font-weight: 600;
      text-decoration: none;
    }
    .badge-origem {
      font-size: 11px;
      padding: 2px 6px;
      border-radius: 4px;
      background-color: #334155;
      color: #cbd5e1;
    }
    .badge-bootstrapped {
      background-color: #1e3a8a;
      color: #93c5fd;
    }
    .badge-estado {
      font-size: 11px;
      padding: 2px 6px;
      border-radius: 4px;
      font-weight: 600;
    }
    .estado-ativa { background-color: rgba(16, 185, 129, 0.15); color: #10b981; }
    .estado-rascunho { background-color: rgba(245, 158, 11, 0.15); color: #f59e0b; }
    .estado-aposentada { background-color: rgba(148, 163, 184, 0.15); color: #94a3b8; }
    .acoes {
      display: flex;
      gap: 6px;
    }
    .btn-xs {
      padding: 3px 8px;
      font-size: 12px;
    }
    .btn-disabled {
      opacity: 0.5;
      pointer-events: none;
    }
    .loading-box {
      padding: var(--space-xl);
      text-align: center;
      background-color: var(--color-bg-surface);
      border-radius: var(--radius-md);
    }
  `]
})
export class CatalogoComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);

  public itens = signal<ItemCatalogoDTO[]>([]);
  public loading = signal<boolean>(false);

  public filtroCodigo: string = '';
  public filtroModoOrigem: string = '';
  public filtroEstado: string = '';

  ngOnInit(): void {
    this.carregarCatalogo();
  }

  public carregarCatalogo(): void {
    this.loading.set(true);
    this.bffClient.getCatalogo({
      codigo: this.filtroCodigo || undefined,
      modoOrigem: this.filtroModoOrigem || undefined,
      estado: this.filtroEstado || undefined
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
}
