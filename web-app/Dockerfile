# Stage 1: build the static bundle
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: serve it with unprivileged nginx (runs as uid 101, listens on 8080), proxying /api to the gateway
FROM nginxinc/nginx-unprivileged:1.31-alpine
# Rendered to /etc/nginx/conf.d/default.conf at start-up by the image's envsubst entrypoint
COPY nginx.conf /etc/nginx/templates/default.conf.template
# Defaults for docker compose; Kubernetes overrides them (kube-dns + the gateway Service FQDN)
ENV NGINX_RESOLVER=127.0.0.11 \
    GATEWAY_UPSTREAM=http://gateway-service:8080 \
    MEDIA_UPSTREAM=http://seaweedfs:8333
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
# Optional trusted-proxy config generated at start from REAL_IP_FROM (see 15-real-ip.sh); the snippets
# directory must be writable by the unprivileged nginx user (uid 101) for that.
COPY --chmod=755 15-real-ip.sh /docker-entrypoint.d/15-real-ip.sh
USER root
RUN chown 101:101 /etc/nginx/snippets
USER 101
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
