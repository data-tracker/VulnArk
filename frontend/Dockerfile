# Vue3 前端：Node 构建 + Nginx 服务
FROM node:18-alpine AS builder

# 国内网络使用 npmmirror 加速
RUN npm config set registry https://registry.npmmirror.com

WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci || npm install
COPY . .
# 跳过 vue-tsc 类型检查，直接构建
RUN npx vite build

########## 运行 ##########
FROM nginx:1.25-alpine

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
