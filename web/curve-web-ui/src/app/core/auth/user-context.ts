export type UserRole = 'CURVE_VIEWER' | 'CURVE_OPERATOR' | 'CURVE_ADMIN';

export interface UserContext {
  id: string;
  name: string;
  email: string;
  roles: UserRole[];
  token: string | null;
}

export const ANONYMOUS_USER: UserContext = {
  id: 'anonymous',
  name: 'Visitante Não Autenticado',
  email: '',
  roles: [],
  token: null
};
