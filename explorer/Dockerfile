# PALIMPSEST explorer image (ARCHITECTURE §3.7, §5.6; WP5). Multi-stage:
# Vite/pnpm build -> static output served by nginx on :80 with /healthz.
# Build with (build context = explorer/):
#   docker build -f explorer/Dockerfile -t palimpsest/explorer:local explorer
# (the `make images` target does this).

# ---- build: Node + pnpm (Vite) --------------------------------------------
# Pinned Node LTS (matches the CI node-version: 22); no floating latest.
FROM node:22.12.0-alpine AS build
WORKDIR /app
# corepack activates the pnpm version pinned in package.json (packageManager).
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# `pnpm build` == tsc --noEmit && vite build -> dist/ (Vite default outDir).
RUN pnpm build

# ---- runtime: nginx static ------------------------------------------------
# Pinned nginx-alpine (no floating latest).
FROM nginx:1.27.3-alpine AS runtime
# SPA config + /healthz. This mirrors deploy/base/palimpsest/explorer-configmap.yaml
# so the image serves /healthz standalone (kind loads this image; the k8s ConfigMap
# mounts the same content over /etc/nginx/conf.d at deploy time).
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
# Healthcheck hits the nginx /healthz target (busybox wget; alpine has no curl).
HEALTHCHECK --interval=15s --timeout=5s --start-period=5s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
# nginx:alpine's base CMD already runs nginx in the foreground.
