import { AppError, formatAppErrorMessage } from './appError';

/** Preserve safe server feedback and trace IDs, hide local parser/runtime failures. */
export default function businessAssetError(error: unknown, fallback: string): string {
  return error instanceof AppError ? formatAppErrorMessage(error) : fallback;
}
