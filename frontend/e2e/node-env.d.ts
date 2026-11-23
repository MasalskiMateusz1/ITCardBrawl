// The e2e code reads only environment variables from Node. The project has no @types/node (no
// extra dependency for it), so this declares just that.
declare const process: { env: Record<string, string | undefined> };
