import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export interface AppError {
  status: number;
  message: string;
  retryable: boolean;
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const appError: AppError = {
        status: error.status,
        message: mapErrorMessage(error.status),
        retryable: error.status >= 500 || error.status === 0
      };
      return throwError(() => appError);
    })
  );
};

function mapErrorMessage(status: number): string {
  switch (true) {
    case status === 0:
      return 'Unable to reach the server. Please check your connection and try again.';
    case status === 404:
      return 'The requested resource was not found.';
    case status >= 500:
      return 'A server error occurred. Please try again in a moment.';
    default:
      return 'An unexpected error occurred. Please try again.';
  }
}

export const correlationIdInterceptor: HttpInterceptorFn = (req, next) => {
  const correlationId = crypto.randomUUID();
  const clonedReq = req.clone({
    setHeaders: { 'X-Correlation-Id': correlationId }
  });
  return next(clonedReq);
};
