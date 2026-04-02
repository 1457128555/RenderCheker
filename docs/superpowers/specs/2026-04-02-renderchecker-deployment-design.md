# RenderChecker 服务端部署设计

**日期：** 2026-04-02  
**状态：** 已批准

## 概述

将 RenderChecker Flask 服务端部署到生产服务器，通过 `chensheng777.top/render/` 公开访问，用于接收和展示 Android 设备的 GLES 兼容性测试报告。

## 需求

1. **访问方式：** 公开访问，任何人都可以查看测试报告统计面板
2. **部署方式：** Docker 容器化部署，与现有服务（Nextcloud、Uptime Kuma、IT-Tools）保持一致
3. **访问路径：** `chensheng777.top/render/`，通过 Nginx 反向代理
4. **博客入口：** 在博客工具页面的在线工具部分添加入口链接
5. **数据持久化：** 保存所有历史测试报告，支持备份
6. **生产环境：** 使用 Gunicorn WSGI 服务器，确保稳定性和性能

## 架构设计

### 整体流量路由

```
外部请求 → 443 端口 → Xray (VLESS+Reality)
               └─ 回落流量 → 127.0.0.1:8443 → Nginx
                    ├─ chensheng777.top → Hugo 博客
                    ├─ chensheng777.top/it-tools/ → IT-Tools (127.0.0.1:8090)
                    ├─ chensheng777.top/render/ → RenderChecker (127.0.0.1:5000) ← 新增
                    └─ cloud.chensheng777.top → Nextcloud (127.0.0.1:8080)
```

### 服务组件

**RenderChecker 容器：**
- 基础镜像：`python:3.11-slim`（轻量级，约 150MB）
- 运行方式：Gunicorn + Flask
- 监听端口：`127.0.0.1:5000`（仅本地访问，通过 Nginx 代理）
- 工作目录：`/root/RenderCheker/server/`
- 数据持久化：Docker volume 挂载 `./data` 目录

**Nginx 配置：**
- 新增 location `/render/`，反向代理到 `http://127.0.0.1:5000`
- 处理路径重写（Flask 应用运行在根路径，Nginx strip prefix）
- 复用现有 SSL 证书（`/root/cert/chensheng777.top/`）

### 数据存储

- SQLite 数据库：`/root/RenderCheker/server/data/reports.db`
- 通过 Docker volume 挂载到容器内的 `/app/data/`
- 宿主机可直接访问数据库文件，便于备份

## 实现细节

### Docker 配置

**Dockerfile：**
```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py .
COPY templates/ templates/

EXPOSE 5000

CMD ["gunicorn", "-w", "4", "-b", "0.0.0.0:5000", "server:app"]
```

**docker-compose.yml：**
```yaml
version: '3.8'

services:
  renderchecker:
    build: .
    container_name: renderchecker
    restart: unless-stopped
    ports:
      - "127.0.0.1:5000:5000"
    volumes:
      - ./data:/app/data
```

### 代码修改

**server.py 调整：**
- 移除 `debug=True`（生产环境不需要）
- 移除 `if __name__ == "__main__"` 块中的 `app.run()`（Gunicorn 直接调用 app 对象）
- 数据库路径保持 `./data/reports.db`（容器内路径）

**requirements.txt 补充：**
```
flask
gunicorn
```

### Nginx 配置

在 `/etc/nginx/sites-enabled/blog` 中添加新的 location：

```nginx
location /render/ {
    proxy_pass http://127.0.0.1:5000/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

注意：`proxy_pass` 末尾的 `/` 会自动 strip `/render/` 前缀。

### 博客入口链接

在博客的工具页面（Hugo 源码）添加链接：
- 链接文本：「RenderChecker - GLES 兼容性测试报告」
- 链接地址：`/render/`
- 位置：在线工具部分

## 部署流程

1. 在 `/root/RenderCheker/server/` 创建 Dockerfile 和 docker-compose.yml
2. 修改 server.py（移除 debug 模式）
3. 补充 requirements.txt（添加 gunicorn）
4. 构建并启动容器：`cd /root/RenderCheker/server && docker compose up -d`
5. 修改 Nginx 配置，添加 `/render/` location
6. 测试 Nginx 配置：`nginx -t`
7. 重载 Nginx：`systemctl reload nginx`
8. 验证服务：访问 `https://chensheng777.top/render/`
9. 在博客工具页面添加入口链接
10. 重新构建博客：`cd /root/myblog && hugo`

## 运维管理

### 服务管理

```bash
cd /root/RenderCheker/server

# 启动服务
docker compose up -d

# 停止服务
docker compose down

# 重启服务
docker compose restart

# 查看日志
docker compose logs -f

# 查看容器状态
docker compose ps
```

### 数据备份

```bash
# 手动备份数据库
cp /root/RenderCheker/server/data/reports.db \
   /root/RenderCheker/server/data/reports.db.backup

# 建议定期备份（可用 cron）
```

### 监控

**添加到 Uptime Kuma：**
- 监控类型：HTTP(s)
- 目标 URL：`https://chensheng777.top/render/`
- 检查间隔：5 分钟
- 告警通知：复用现有飞书 Webhook

**资源监控：**
```bash
# 查看容器资源占用
docker stats renderchecker
```

## 影响范围

- **不影响现有服务：** Xray、Nginx、Nextcloud、Uptime Kuma、IT-Tools 均不受影响
- **新增资源占用：**
  - 内存：约 50-100MB
  - 磁盘：约 200MB（镜像 + 数据）
  - 端口：127.0.0.1:5000（仅本地）
- **配置变更：**
  - Nginx 新增一个 location
  - 博客工具页面新增一个链接

## 技术选型理由

### 为什么选择 Docker + Gunicorn？

1. **与现有服务一致：** 服务器已有 3 个 Docker 服务运行，管理方式统一
2. **容器隔离：** 不污染系统环境，依赖管理清晰
3. **生产级稳定性：** Gunicorn 是成熟的 WSGI 服务器，适合公开访问的服务
4. **易于迁移：** 容器化后可轻松迁移到其他服务器
5. **资源充足：** 服务器内存已升级到 2GB，当前可用 756MB，足够运行

### 为什么不选择其他方案？

- **systemd + Gunicorn：** 虽然内存占用更低，但与现有服务管理方式不一致
- **systemd + Flask 内置服务器：** Flask 内置服务器不适合生产环境，性能和稳定性差

## 安全考虑

- **端口绑定：** 仅绑定 127.0.0.1:5000，不直接暴露到公网
- **反向代理：** 通过 Nginx 统一处理 HTTPS 和请求头
- **数据隔离：** 容器内运行，与宿主机隔离
- **无认证机制：** 当前设计为公开访问，未来如需限制可添加 Nginx basic auth

## 未来扩展

- **API 认证：** 如需限制上传 API，可添加 token 认证
- **数据清理：** 如数据库过大，可添加定期清理旧报告的脚本
- **性能优化：** 如流量增大，可调整 Gunicorn worker 数量或切换到 PostgreSQL
- **监控增强：** 可添加更详细的应用级监控（如响应时间、错误率）
