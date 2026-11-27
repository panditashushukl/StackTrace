export interface User {
  id: string;
  email: string;
  name: string;
  username: string;
  avatarUrl?: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
  hasPassword?: boolean;
  googleLinked?: boolean;
}

export interface AuthResponse {
  user: User;
}

export interface RegisterPayload {
  name: string;
  username: string;
  email: string;
  password: string;
}

export interface LoginPayload {
  identifier: string;
  password: string;
}
