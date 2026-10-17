FROM --platform=$BUILDPLATFORM ghcr.io/fwangchanju/marketry-assets:latest AS assets

FROM nginx:alpine
LABEL org.opencontainers.image.source=https://github.com/fwangchanju/marketry-backend
COPY --from=assets /dist /usr/share/nginx/html
COPY infra/nginx.conf /etc/nginx/conf.d/default.conf
