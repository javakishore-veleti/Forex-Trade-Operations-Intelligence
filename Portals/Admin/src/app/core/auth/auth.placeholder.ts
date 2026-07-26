import { Injectable } from '@angular/core';

/**
 * AUTH PLACEHOLDER — Phase-N Implementation
 *
 * Token-based authentication will be wired here in a later phase.
 * This service currently provides stub methods for the auth contract.
 * No credential values are committed to the repository.
 */
@Injectable({ providedIn: 'root' })
export class AuthPlaceholderService {
  // TODO: Implement token-based auth (OAuth2/OIDC) in Phase-N
  getToken(): string | null {
    return null;
  }

  isAuthenticated(): boolean {
    // Placeholder: always returns true for development
    return true;
  }

  getUserRole(): string {
    // Placeholder: returns default role
    return 'OPERATIONS_USER';
  }

  logout(): void {
    // TODO: Clear token and redirect to login
  }
}
