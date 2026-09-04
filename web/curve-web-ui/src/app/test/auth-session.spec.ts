import { describe, it, expect, beforeEach } from 'vitest';
import { AuthService } from '../core/auth/auth.service.ts';
import { FormPreservationService } from '../core/state/form-preservation.service.ts';
import { UserContext } from '../core/auth/user-context.ts';

function contextoDeTeste(roles: UserContext['roles'], token: string = 'token-de-teste'): UserContext {
  return { id: 'usr-teste', name: 'Usuário de Teste', email: 'teste@poc-curvas.local', roles, token };
}

describe('AuthService & FormPreservationService — Autenticação e Gestão de Sessão', () => {
  let authService: AuthService;
  let formService: FormPreservationService;

  beforeEach(() => {
    formService = new FormPreservationService();
    authService = new AuthService();
  });

  it('deve identificar corretamente os perfis CURVE_VIEWER, CURVE_OPERATOR e CURVE_ADMIN', () => {
    authService.aplicarContextoParaTeste(contextoDeTeste(['CURVE_VIEWER']));
    expect(authService.isViewer()).toBe(true);
    expect(authService.isOperator()).toBe(false);
    expect(authService.isAdmin()).toBe(false);

    authService.aplicarContextoParaTeste(contextoDeTeste(['CURVE_VIEWER', 'CURVE_OPERATOR']));
    expect(authService.isViewer()).toBe(true);
    expect(authService.isOperator()).toBe(true);
    expect(authService.isAdmin()).toBe(false);

    authService.aplicarContextoParaTeste(contextoDeTeste(['CURVE_VIEWER', 'CURVE_OPERATOR', 'CURVE_ADMIN']));
    expect(authService.isViewer()).toBe(true);
    expect(authService.isOperator()).toBe(true);
    expect(authService.isAdmin()).toBe(true);
  });

  it('sem sessão nenhuma, começa como visitante não autenticado (sem token, sem papel nenhum)', () => {
    expect(authService.isAuthenticated()).toBe(false);
    expect(authService.hasRole('CURVE_VIEWER')).toBe(false);
    expect(authService.getToken()).toBeNull();
  });

  it('deve fornecer mensagem explicativa quando o perfil não tem permissão para uma ação', () => {
    const adminMsg = authService.getPermissionExplanation('CURVE_ADMIN');
    expect(adminMsg).toContain('Administradores');

    const operMsg = authService.getPermissionExplanation('CURVE_OPERATOR');
    expect(operMsg).toContain('Operadores');
  });

  it('deve preservar dados de formulário durante expiração de sessão e recuperá-los depois', () => {
    const rascunhoCurva = {
      codigo: 'DOL',
      nome: 'Curva de Dólar Cupom Limpo',
      horarioLimitePublicacao: '18:30'
    };

    formService.saveDraft('nova_curva', rascunhoCurva);

    // Simula expiração de sessão
    authService.handleSessionExpired();
    expect(authService.isSessionExpired()).toBe(true);

    // Recupera dados preservados
    const recuperado = formService.getDraft<typeof rascunhoCurva>('nova_curva');
    expect(recuperado).toEqual(rascunhoCurva);

    // Renovação real de sessão exige um Keycloak real (authService.reauthenticate()
    // chama keycloak.updateToken() de verdade) — fora do escopo de um teste de
    // unidade sem IdP; aqui só provamos que reaplicar um contexto novo (o que a
    // renovação real faz internamente) limpa a flag de sessão expirada.
    authService.aplicarContextoParaTeste(contextoDeTeste(['CURVE_VIEWER'], 'novo-token-jwt'));
    expect(authService.isSessionExpired()).toBe(false);
    expect(authService.getToken()).toBe('novo-token-jwt');

    // Limpa rascunho após salvar
    formService.clearDraft('nova_curva');
    expect(formService.getDraft('nova_curva')).toBeNull();
  });
});
