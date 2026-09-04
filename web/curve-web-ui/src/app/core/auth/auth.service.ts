import { Injectable, signal, computed } from '@angular/core';
import Keycloak from 'keycloak-js';
import { UserContext, UserRole, ANONYMOUS_USER } from './user-context.ts';

const PAPEIS_CONHECIDOS: UserRole[] = ['CURVE_VIEWER', 'CURVE_OPERATOR', 'CURVE_ADMIN'];

/**
 * Corrigido na auditoria desta sessão: a versão anterior tinha um usuário
 * fixo, com os três perfis sempre concedidos e um token falso
 * (`'mock-jwt-token-dev'`) como estado padrão — nunca havia login real, e um
 * seletor de perfil na tela dava qualquer visitante virar Administrador com
 * um clique, sem credencial nenhuma. Agora usa `keycloak-js` de verdade,
 * contra o mesmo realm `curvas` que `curve-api`/`curve-bff` validam
 * (`RuntimeConfigService.authProviderUrl`/`clientId`) — os papéis vêm da
 * claim `realm_access.roles` do token real, mesmo lugar que o lado Java lê
 * (`GrantedAuthoritiesExtractor`).
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private keycloak: Keycloak | null = null;

  private currentUserSignal = signal<UserContext>(ANONYMOUS_USER);
  private sessionExpiredSignal = signal<boolean>(false);
  private returnUrl: string = '/';

  public currentUser = this.currentUserSignal.asReadonly();
  public isSessionExpired = this.sessionExpiredSignal.asReadonly();

  public isAuthenticated = computed(() => !!this.currentUserSignal().token);
  public isViewer = computed(() => this.hasRole('CURVE_VIEWER'));
  public isOperator = computed(() => this.hasRole('CURVE_OPERATOR'));
  public isAdmin = computed(() => this.hasRole('CURVE_ADMIN'));

  /**
   * Inicializa a sessão real contra o Keycloak — chamado uma vez, no
   * bootstrap da aplicação (ver `app.config.ts`), depois que
   * `RuntimeConfigService` já carregou `authProviderUrl`/`clientId`.
   * `onLoad: 'login-required'` redireciona para o login real do Keycloak se
   * não houver sessão válida — nunca entra na aplicação sem autenticação.
   *
   * @param authProviderUrl URL completa até o realm (ex.:
   *   "http://localhost:8180/realms/curvas", `RuntimeConfigService`) —
   *   `keycloak-js` espera a URL base do servidor mais o nome do realm em
   *   separado, então os dois são extraídos daqui.
   */
  public async init(authProviderUrl: string, clientId: string): Promise<void> {
    const match = /^(.*)\/realms\/([^/]+)\/?$/.exec(authProviderUrl);
    if (!match) {
      throw new Error(`authProviderUrl fora do formato esperado ".../realms/<realm>": "${authProviderUrl}"`);
    }
    const [, url, realm] = match;

    this.keycloak = new Keycloak({ url, realm, clientId });

    const autenticado = await this.keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false
    });

    if (!autenticado) {
      this.currentUserSignal.set(ANONYMOUS_USER);
      return;
    }

    this.atualizarUsuarioAPartirDoToken();

    this.keycloak.onTokenExpired = () => {
      this.keycloak?.updateToken(30)
        .then((renovado: boolean) => {
          if (renovado) {
            this.atualizarUsuarioAPartirDoToken();
          }
        })
        .catch(() => this.handleSessionExpired());
    };
  }

  private atualizarUsuarioAPartirDoToken(): void {
    if (!this.keycloak?.tokenParsed) {
      this.currentUserSignal.set(ANONYMOUS_USER);
      return;
    }

    const claims = this.keycloak.tokenParsed as {
      sub?: string;
      preferred_username?: string;
      email?: string;
      realm_access?: { roles?: string[] };
    };

    const papeis = (claims.realm_access?.roles ?? [])
      .filter((papel): papel is UserRole => PAPEIS_CONHECIDOS.includes(papel as UserRole));

    this.currentUserSignal.set({
      id: claims.sub ?? 'desconhecido',
      name: claims.preferred_username ?? 'Usuário autenticado',
      email: claims.email ?? '',
      roles: papeis,
      token: this.keycloak.token ?? null
    });
    this.sessionExpiredSignal.set(false);
  }

  public hasRole(role: UserRole): boolean {
    return this.currentUserSignal().roles.includes(role);
  }

  /**
   * Só para teste de unidade: injeta um `UserContext` diretamente, sem
   * passar pelo Keycloak real — mesmo espírito de testar a lógica de
   * autorização com um JWT sintético (`SecurityMockMvcRequestPostProcessors.jwt()`
   * do lado Java), sem precisar de um IdP real rodando. Nunca chamado pelo
   * fluxo real de login — só pela suíte de testes.
   */
  public aplicarContextoParaTeste(context: UserContext): void {
    this.currentUserSignal.set(context);
    this.sessionExpiredSignal.set(false);
  }

  public getToken(): string | null {
    return this.currentUserSignal().token;
  }

  public setReturnUrl(url: string): void {
    this.returnUrl = url;
  }

  public getReturnUrl(): string {
    return this.returnUrl || '/';
  }

  public handleSessionExpired(): void {
    this.sessionExpiredSignal.set(true);
  }

  /** Renova a sessão real junto ao Keycloak — nunca aceita um token passado por quem chama. */
  public async reauthenticate(): Promise<void> {
    if (!this.keycloak) {
      return;
    }
    try {
      const renovado = await this.keycloak.updateToken(-1); // -1 força renovação mesmo se ainda válido
      if (renovado || this.keycloak.token) {
        this.atualizarUsuarioAPartirDoToken();
      }
    } catch {
      this.keycloak.login();
    }
  }

  public logout(): void {
    if (this.keycloak) {
      this.keycloak.logout({ redirectUri: window.location.origin });
      return;
    }
    this.currentUserSignal.set(ANONYMOUS_USER);
  }

  public getPermissionExplanation(requiredRole: UserRole): string {
    switch (requiredRole) {
      case 'CURVE_ADMIN':
        return 'Operação restrita a Administradores da Plataforma.';
      case 'CURVE_OPERATOR':
        return 'Operação restrita a Operadores de Tesouraria e Administradores.';
      case 'CURVE_VIEWER':
        return 'Acesso exige autenticação na plataforma.';
      default:
        return 'Você não possui permissão para esta ação.';
    }
  }
}
