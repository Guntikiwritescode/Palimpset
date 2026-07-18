// MSW browser worker — the explorer is mock-backed until the generated SDK lands.
// Started in src/main.tsx before render. The worker script is public/mockServiceWorker.js.
import { setupWorker } from "msw/browser";
import { handlers } from "./handlers";

export const worker = setupWorker(...handlers);

export async function startMockWorker(): Promise<void> {
  await worker.start({
    onUnhandledRequest: "bypass",
    serviceWorker: { url: `${import.meta.env.BASE_URL}mockServiceWorker.js` },
    quiet: true,
  });
}
