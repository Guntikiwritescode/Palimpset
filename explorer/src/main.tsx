import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import "./styles.css";

// The explorer is mock-backed until the generated SDK lands (BUILD-CONTRACT §7).
// Start the MSW worker before render so no request escapes to a non-existent backend.
async function bootstrap() {
  const { startMockWorker } = await import("./mocks/browser");
  await startMockWorker();
  ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}

void bootstrap();
