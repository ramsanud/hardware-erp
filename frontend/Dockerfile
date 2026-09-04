# CR-059 - the self-hosted frontend. Multi-stage: the final image is nginx
# plus static files, with no node_modules and no source.
#
# The hosted deployment does NOT use this file - there the frontend is built
# and served by Vercel, which does its own build. This exists so that a client
# with no internet-facing infrastructure gets the same UI from one
# `docker compose up`.

FROM node:22-alpine AS build
WORKDIR /build

# Dependencies resolve from the lockfile alone, before sources are copied, so
# editing a component does not re-download the tree. npm ci - not install -
# because a reproducible client build must use the locked versions.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# VITE_API_BASE_URL is deliberately left EMPTY. Vite inlines env vars at build
# time, so a value baked in here would be wrong for every install that is not
# the one it was built on. Empty means the app calls a relative /api, which
# nginx below proxies to the backend on the same origin - so the refresh
# cookie (SameSite=Strict) is sent, and CORS never enters the picture.
ENV VITE_API_BASE_URL=""

# `npm run build` is `tsc -b && vite build`: a type error fails the image
# build rather than shipping a broken bundle.
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Runs as the nginx image's own non-root worker; only the master process
# needs to bind, and 80 inside the container is remapped on the host by
# docker-compose.selfhosted.yml.
EXPOSE 80

# The compose healthcheck hits this. Plain 200 from a static file, so it
# reports the web tier only and never masks a database problem as healthy -
# the backend has its own /api/actuator/health probe for that.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://localhost/healthz || exit 1
