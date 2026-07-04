# SAssist server -- portable container for cloud hosting
FROM node:20-alpine AS build
WORKDIR /app
# toolchain for the better-sqlite3 native addon (musl builds from source if no prebuilt)
RUN apk add --no-cache python3 make g++
COPY package*.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV DATA_DIR=/data
COPY package*.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
RUN mkdir -p /data
VOLUME /data
EXPOSE 8080
CMD ["node", "dist/src/index.js"]
