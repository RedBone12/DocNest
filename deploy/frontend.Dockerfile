FROM node:22-alpine AS build
WORKDIR /source
COPY frontend/distributed-document-search-engine-ui-main/package*.json ./
RUN npm ci
COPY frontend/distributed-document-search-engine-ui-main/ ./
ENV VITE_API_BASE_URL=/api
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /source/dist /usr/share/nginx/html
EXPOSE 8080
