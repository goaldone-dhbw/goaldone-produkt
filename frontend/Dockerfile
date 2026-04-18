# Stage 1: Build (Node + JDK for openapi-generator)
FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache nodejs npm

WORKDIR /build

# Copy api-spec and frontend (openapi-generator needs api-spec)
COPY api-spec/ api-spec/
COPY frontend/ frontend/

WORKDIR /build/frontend

# Install dependencies
RUN npm ci

# Generate API client from OpenAPI spec
RUN npm run generate-api

# Build Angular app for production
RUN npm run build

# Stage 2: Runtime (nginx)
FROM nginx:alpine

# Copy built Angular app to nginx
COPY --from=builder /build/frontend/dist/frontend/browser/ /usr/share/nginx/html/

# Copy nginx config template
COPY frontend/nginx.conf /etc/nginx/templates/default.conf.template

# Copy env.js template for runtime substitution
COPY frontend/src/assets/env.js.template /usr/share/nginx/html/assets/env.js.template

# Copy and make entrypoint executable
COPY frontend/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
