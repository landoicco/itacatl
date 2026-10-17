# Stage 1: Build the production assets
FROM node:25-alpine AS builder
WORKDIR /app

COPY package*.json ./
# npm ci installs exactly what package-lock.json pins, so builds are reproducible
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve with the official nginx image built to run as a non-root user.
# Unprivileged users cannot bind ports below 1024, so it listens on 8080.
FROM nginxinc/nginx-unprivileged:alpine AS runner
WORKDIR /usr/share/nginx/html
# The default files are owned by root, so remove them as root and drop back to nginx (UID 101)
USER root
RUN rm -rf ./*
USER 101
COPY --from=builder /app/dist .
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
