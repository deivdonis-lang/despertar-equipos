import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// "./" = rutas relativas: funciona igual en GitHub Pages (/despertar-equipos/) que en local.
export default defineConfig({
  base: "./",
  plugins: [react()],
  server: { host: "::", port: 8080 },
});
