import { Injectable } from '@angular/core';

/**
 * AUTH PLACEHOLDER — Phase-N Implementation
 *
 * Token-based authentication will be wired here in a later phase.
 * No credential values are committed to the repository.
 */
@Injectable({ providedIn: 'root' })
export class AuthPlaceholderService {
  // TODO: Implement token-based auth (OAuth2/OIDC) in Phase-N
  getToken(): string | null {
    return null;
  }

  isAuthenticated(): boolean {
    return true;
  }

  logout(): void {
    // TODO: Clear token and redirect to login
  }
}
