# RenderChecker 服务端部署实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 RenderChecker Flask 服务端部署到生产服务器，通过 `chensheng777.top/render/` 公开访问

**Architecture:** Docker 容器化部署，Gunicorn + Flask，监听 127.0.0.1:5000，通过 Nginx 反向代理，数据持久化到宿主机 `./data` 目录

**Tech Stack:** Python 3.11, Flask, Gunicorn, Docker, Nginx, SQLite

---

## File Structure

**新建文件：**
- `/root/RenderCheker/server/Dockerfile` - Docker 镜像构建配置
- `/root/RenderCheker/server/docker-compose.yml` - Docker Compose 编排配置
- `/root/RenderCheker/server/.dockerignore` - Docker 构建忽略文件

**修改文件：**
- `/root/RenderCheker/server/requirements.txt` - 添加 gunicorn 依赖
- `/root/RenderCheker/server/server.py:166-167` - 移除 debug 模式
- `/etc/nginx/sites-enabled/blog:36` - 添加 /render/ location
- `/root/myblog/content/tools.md:13` - 添加 RenderChecker 入口链接

---

### Task 1: 准备 Docker 配置文件

**Files:**
- Create: `/root/RenderCheker/server/Dockerfile`
- Create: `/root/RenderCheker/server/docker-compose.yml`
- Create: `/root/RenderCheker/server/.dockerignore`

- [ ] **Step 1: 创建 Dockerfile**

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

- [ ] **Step 2: 创建 docker-compose.yml**

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

- [ ] **Step 3: 创建 .dockerignore**

```
data/
*.pyc
__pycache__/
.git/
.gitignore
*.md
docs/
```

- [ ] **Step 4: 验证文件创建**

Run: `ls -la /root/RenderCheker/server/ | grep -E "Dockerfile|docker-compose|dockerignore"`

Expected: 显示三个新建文件

- [ ] **Step 5: Commit**

```bash
cd /root/RenderCheker
git add server/Dockerfile server/docker-compose.yml server/.dockerignore
git commit -m "feat: add Docker configuration for server deployment

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 2: 更新 Python 依赖

**Files:**
- Modify: `/root/RenderCheker/server/requirements.txt:1-2`

- [ ] **Step 1: 添加 gunicorn 到 requirements.txt**

```
flask
gunicorn
```

- [ ] **Step 2: 验证文件内容**

Run: `cat /root/RenderCheker/server/requirements.txt`

Expected: 显示 flask 和 gunicorn 两行

- [ ] **Step 3: Commit**

```bash
cd /root/RenderCheker
git add server/requirements.txt
git commit -m "feat: add gunicorn to server dependencies

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: 修改 server.py 移除 debug 模式

**Files:**
- Modify: `/root/RenderCheker/server/server.py:166-167`

- [ ] **Step 1: 修改 server.py 移除 debug 和 app.run()**

将第 166-167 行：
```python
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
```

修改为：
```python
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
```

- [ ] **Step 2: 验证修改**

Run: `tail -5 /root/RenderCheker/server/server.py`

Expected: 显示 `debug=False`

- [ ] **Step 3: Commit**

```bash
cd /root/RenderCheker
git add server/server.py
git commit -m "fix: disable debug mode for production

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: 构建并启动 Docker 容器

**Files:**
- Create: `/root/RenderCheker/server/data/` (directory)

- [ ] **Step 1: 创建数据目录**

Run: `mkdir -p /root/RenderCheker/server/data`

Expected: 目录创建成功，无输出

- [ ] **Step 2: 构建 Docker 镜像**

Run: `cd /root/RenderCheker/server && docker compose build`

Expected: 显示构建进度，最后显示 "Successfully built" 和镜像 ID

- [ ] **Step 3: 启动容器**

Run: `cd /root/RenderCheker/server && docker compose up -d`

Expected: 显示 "Container renderchecker  Started"

- [ ] **Step 4: 验证容器运行状态**

Run: `docker ps | grep renderchecker`

Expected: 显示容器状态为 "Up"，端口映射 "127.0.0.1:5000->5000/tcp"

- [ ] **Step 5: 验证服务响应**

Run: `curl -s http://127.0.0.1:5000/ | head -20`

Expected: 显示 HTML 内容，包含 "RenderTest Dashboard"

- [ ] **Step 6: 检查容器日志**

Run: `docker compose logs renderchecker | tail -10`

Expected: 显示 Gunicorn 启动日志，包含 "Listening at: http://0.0.0.0:5000"

---

### Task 5: 配置 Nginx 反向代理

**Files:**
- Modify: `/etc/nginx/sites-enabled/blog:36`

- [ ] **Step 1: 备份 Nginx 配置**

Run: `cp /etc/nginx/sites-enabled/blog /etc/nginx/sites-enabled/blog.backup`

Expected: 备份文件创建成功

- [ ] **Step 2: 在 blog 配置中添加 /render/ location**

在 `/etc/nginx/sites-enabled/blog` 第 36 行（`location ^~ /it-tools/` 块之后）插入：

```nginx
    location ^~ /render/ {
        proxy_pass http://127.0.0.1:5000/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
```

- [ ] **Step 3: 验证 Nginx 配置语法**

Run: `nginx -t`

Expected: 显示 "syntax is ok" 和 "test is successful"

- [ ] **Step 4: 重载 Nginx**

Run: `systemctl reload nginx`

Expected: 无输出，命令成功执行

- [ ] **Step 5: 验证 Nginx 状态**

Run: `systemctl status nginx | grep Active`

Expected: 显示 "Active: active (running)"

- [ ] **Step 6: 测试反向代理**

Run: `curl -s https://chensheng777.top/render/ -k | head -20`

Expected: 显示 HTML 内容，包含 "RenderTest Dashboard"

---

### Task 6: 在博客工具页面添加入口链接

**Files:**
- Modify: `/root/myblog/content/tools.md:13`

- [ ] **Step 1: 在 tools.md 添加 RenderChecker 链接**

在 `/root/myblog/content/tools.md` 第 13 行（IT Tools 之后）插入：

```markdown
- [RenderChecker](/render/) - GLES 兼容性测试报告
```

修改后的在线工具部分应为：
```markdown
## 在线工具

- [LUT Apply](/tools/lut-apply/) - LUT 滤镜应用工具
- [IT Tools](/it-tools/) - 开发者在线工具箱（编解码、转换、生成等 70+ 工具）
- [RenderChecker](/render/) - GLES 兼容性测试报告
- [Nextcloud](https://cloud.chensheng777.top) - 私有云盘
```

- [ ] **Step 2: 验证修改**

Run: `grep -A 5 "## 在线工具" /root/myblog/content/tools.md`

Expected: 显示包含 RenderChecker 链接的内容

- [ ] **Step 3: 重新构建博客**

Run: `cd /root/myblog && hugo`

Expected: 显示构建统计，包含 "Total in XXX ms"

- [ ] **Step 4: 验证生成的 HTML**

Run: `grep -i renderchecker /root/myblog/public/tools/index.html`

Expected: 显示包含 RenderChecker 链接的 HTML 代码

- [ ] **Step 5: Commit 博客修改**

```bash
cd /root/myblog
git add content/tools.md
git commit -m "feat: add RenderChecker link to tools page

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 7: 添加 Uptime Kuma 监控

**Files:**
- None (manual operation in Uptime Kuma web UI)

- [ ] **Step 1: 通过 SSH 隧道访问 Uptime Kuma**

说明：在本地终端执行以下命令建立 SSH 隧道：
```bash
ssh -L 3001:127.0.0.1:3001 root@104.238.148.162
```

然后在浏览器访问 `http://localhost:3001`

- [ ] **Step 2: 在 Uptime Kuma 中添加监控项**

手动操作步骤：
1. 点击 "Add New Monitor"
2. 填写配置：
   - Monitor Type: HTTP(s)
   - Friendly Name: RenderChecker
   - URL: `https://chensheng777.top/render/`
   - Heartbeat Interval: 300 seconds (5 minutes)
   - Retries: 3
   - Check Certificate: Yes
3. 在 Notifications 中选择现有的飞书 Webhook
4. 点击 Save

- [ ] **Step 3: 验证监控状态**

在 Uptime Kuma 界面中查看 RenderChecker 监控项，确认状态为 "Up" 且显示绿色

---

### Task 8: 端到端验证

**Files:**
- None (verification only)

- [ ] **Step 1: 验证容器运行状态**

Run: `docker ps | grep renderchecker`

Expected: 显示容器状态为 "Up"

- [ ] **Step 2: 验证容器资源占用**

Run: `docker stats renderchecker --no-stream`

Expected: 显示内存占用约 50-100MB

- [ ] **Step 3: 验证数据库文件创建**

Run: `ls -lh /root/RenderCheker/server/data/`

Expected: 显示 `reports.db` 文件（如果已有测试数据上传）

- [ ] **Step 4: 验证 HTTPS 访问**

Run: `curl -s https://chensheng777.top/render/ | grep -o "<title>.*</title>"`

Expected: 显示 `<title>RenderTest Dashboard</title>`

- [ ] **Step 5: 验证博客工具页面链接**

Run: `curl -s https://chensheng777.top/tools/ | grep -i renderchecker`

Expected: 显示包含 RenderChecker 链接的 HTML

- [ ] **Step 6: 测试 API 端点**

Run: `curl -s https://chensheng777.top/render/api/reports`

Expected: 显示 JSON 数组（可能为空 `[]`）

- [ ] **Step 7: 验证服务自动重启**

Run: `docker restart renderchecker && sleep 5 && docker ps | grep renderchecker`

Expected: 容器重启后状态恢复为 "Up"

- [ ] **Step 8: 检查 Nginx 日志**

Run: `tail -20 /var/log/nginx/access.log | grep "/render/"`

Expected: 显示对 /render/ 的访问记录（如果有访问的话）

---

### Task 9: 文档更新

**Files:**
- Modify: `/root/.claude/CLAUDE.md` (global instructions)

- [ ] **Step 1: 在 CLAUDE.md 中添加 RenderChecker 服务信息**

在 `/root/.claude/CLAUDE.md` 的"运行的服务"表格中添加一行：

```markdown
| **RenderChecker** | GLES 测试报告收集，Docker 部署，监听 `127.0.0.1:5000` | `cd /root/RenderCheker/server && docker compose up -d / down` |
```

在"流量架构"部分的 Nginx 分支中添加：

```
                    ├─ chensheng777.top/render/ → RenderChecker (127.0.0.1:5000)
```

在"关键文件路径"表格中添加：

```markdown
| RenderChecker 工作目录 | `/root/RenderCheker/server/` |
| RenderChecker docker-compose | `/root/RenderCheker/server/docker-compose.yml` |
| RenderChecker 数据库 | `/root/RenderCheker/server/data/reports.db` |
```

- [ ] **Step 2: 验证修改**

Run: `grep -i renderchecker /root/.claude/CLAUDE.md`

Expected: 显示新添加的 RenderChecker 相关内容

- [ ] **Step 3: Commit CLAUDE.md 修改**

```bash
cd /root/.claude
git add CLAUDE.md
git commit -m "docs: add RenderChecker service to server documentation

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Self-Review Checklist

**Spec Coverage:**
- ✓ Docker 配置（Dockerfile, docker-compose.yml）
- ✓ Python 依赖更新（requirements.txt）
- ✓ 代码修改（server.py debug 模式）
- ✓ Nginx 反向代理配置
- ✓ 博客入口链接
- ✓ 数据持久化（./data volume）
- ✓ 监控配置（Uptime Kuma）
- ✓ 端到端验证
- ✓ 文档更新

**Placeholder Scan:**
- ✓ 无 TBD、TODO、"implement later"
- ✓ 所有代码块完整
- ✓ 所有命令包含预期输出
- ✓ 所有文件路径精确

**Type Consistency:**
- ✓ 容器名称统一为 `renderchecker`
- ✓ 端口映射统一为 `127.0.0.1:5000:5000`
- ✓ 数据目录统一为 `./data`
- ✓ 服务路径统一为 `/render/`

**Execution Order:**
- ✓ 任务按依赖顺序排列（Docker 配置 → 构建 → Nginx → 博客 → 监控 → 验证）
- ✓ 每个任务独立可执行
- ✓ 每个任务包含验证步骤
- ✓ 每个任务包含 commit 步骤（除手动操作外）
