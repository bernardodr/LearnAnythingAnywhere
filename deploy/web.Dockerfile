# The laa-web image: Caddy serving the exported web app. It's also the front door for the whole server: it fetches
# and renews the HTTPS certificates and passes the API's address on to laa-server (deploy/Caddyfile).
#
# Built from the repository root: `docker build -f deploy/web.Dockerfile --build-arg EXPO_PUBLIC_API_URL=... .`
# (deploy.sh and the deploy workflow do this for you). web.Dockerfile.dockerignore lets in only the app's sources and
# the Caddyfile, so `.env` and other secrets can never reach the image.
#
# The API's address is baked into the web app when it's exported, so it's a build argument: an image built for
# https://api.learnanythinganywhere.app talks only to that address. The site addresses Caddy answers on are read at
# run time (LAA_WEB_URL and LAA_API_URL).

# ---- Export the web app: plain files (HTML, JavaScript, fonts, images) in dist/.
FROM node:24-bookworm-slim AS export
WORKDIR /app
ENV CI=1 EXPO_NO_TELEMETRY=1
COPY app/package.json app/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY app/ ./
ARG EXPO_PUBLIC_API_URL
RUN test -n "$EXPO_PUBLIC_API_URL" || { echo "Set the EXPO_PUBLIC_API_URL build argument (the API's address)." >&2; exit 1; }
RUN EXPO_PUBLIC_API_URL="$EXPO_PUBLIC_API_URL" npx expo export --platform web --output-dir dist

# ---- Serve: Caddy with the files and its configuration.
FROM caddy:2.11-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=export /app/dist /srv
