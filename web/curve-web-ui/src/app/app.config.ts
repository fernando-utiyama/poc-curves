import { ApplicationConfig, APP_INITIALIZER, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes.ts';
import { authInterceptor } from './core/auth/auth.interceptor.ts';
import { AuthService } from './core/auth/auth.service.ts';
import { RuntimeConfigService } from './core/config/runtime-config.service.ts';

export function initializeAppConfig(configService: RuntimeConfigService) {
  return () => configService.loadConfig();
}

/**
 * Roda depois de `initializeAppConfig` (registrado em seguida na lista de
 * providers — `APP_INITIALIZER` multi executa na ordem declarada) para
 * `authProviderUrl`/`clientId` já estarem carregados de `config.json`.
 * Redireciona para o login real do Keycloak se não houver sessão válida —
 * corrigido na auditoria desta sessão, que encontrou a aplicação inteira
 * sem autenticação real nenhuma.
 */
export function initializeAuth(authService: AuthService, configService: RuntimeConfigService) {
  return () => authService.init(configService.get('authProviderUrl'), configService.get('clientId'));
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAppConfig,
      deps: [RuntimeConfigService],
      multi: true
    },
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuth,
      deps: [AuthService, RuntimeConfigService],
      multi: true
    }
  ]
};
