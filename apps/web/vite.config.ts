import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, "../..", "");
  const apiPort = Number(env.API_PORT || 8080);
  const webPort = Number(env.WEB_PORT || 5173);

  return {
    envDir: "../..",
    plugins: [react(), tailwindcss()],
    server: {
      port: webPort,
      strictPort: true,
      proxy: { "/console/api": `http://localhost:${apiPort}` },
    },
    test: { environment: "jsdom", setupFiles: "./src/test/setup.ts" },
  };
});
