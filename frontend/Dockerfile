FROM node:24.15.0-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build:prod

FROM nginx:1.28-alpine AS runtime

RUN rm /etc/nginx/conf.d/default.conf \
    && mkdir -p /var/cache/nginx /var/run /tmp/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/run /tmp/nginx /etc/nginx/conf.d

COPY --from=build /workspace/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1

