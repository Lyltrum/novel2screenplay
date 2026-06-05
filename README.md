# novel2screenplay · AI 小说转剧本工具

将 **3 章以上的小说文本**自动转换为**结构化剧本（YAML）**，让作者快速获得可编辑、可进一步打磨的剧本初稿。

- 输入：一段小说全文（自动识别章节）
- 输出：结构化剧本 YAML（或行业标准 Fountain 格式）
- 定位：降低小说改编门槛，产出"可信、可校对、可手改"的初稿

> Schema 设计与设计原因详见 [`docs/screenplay-schema.md`](docs/screenplay-schema.md)。
> 完整示例输出见 [`examples/sample-output.yml`](examples/sample-output.yml)。

---

## ✨ 亮点

| 亮点 | 说明 |
|---|---|
| **来源可追溯** | 每个场景回填原文出处（章节号 + 原文片段），作者可逐场对照原文校验改编是否忠实——把"AI 黑盒"变成"可验证的辅助初稿" |
| **自检 + 自动修复闭环** | 生成后自动体检（必填字段、对白角色是否在登记表、时间是否中文、来源是否完整），有问题则带着问题清单让模型自我修复（有界 ≤2 轮），残留问题数通过响应头暴露 |
| **跨章人物一致性** | 逐章建立人物登记表（主名 + 别名 + 设定）并合并去重，注入每个场景生成，保证全篇称呼统一（如"铁面人 = 苏窈的兄长"被正确归并） |
| **多格式 + 多风格** | 同时支持导出 YAML 与 Fountain；改编风格可选（电影 / 话剧 / 短剧 / 分镜） |

---

## 🧱 技术栈

- Java 17 · Spring Boot 3.4.5
- Spring AI Alibaba（`spring-ai-alibaba-starter-dashscope`，BOM `1.0.0.2`）直连百炼，驱动通义千问 Qwen
- 结构化抽取：Spring AI 结构化输出 `.entity()`（record 即 Schema 的单一事实源）
- YAML 序列化：`jackson-dataformat-yaml`

---

## 🗂️ 架构（按职责切分，一个目录只做一件事）

```
split/      章节切分          bible/      跨章人物/地点登记
extract/    场景抽取·标题生成  assemble/   全局编号·组装
validate/   体检 + 修复闭环    export/     YAML / Fountain 导出
style/      风格指引          prompt/     集中的提示词
pipeline/   流水线编排        controller/ REST 入口
model/      Schema record     config/     Bean 装配
```

流水线顺序：**切章 → 逐章建立完整登记表 → 注入登记表与风格逐章抽场景 → 生成剧名/梗概 → 全局编号组装 → 自检修复闭环 → 导出**。

---

## 🚀 快速开始

### 1. 配置 API Key（二选一）

百炼控制台领取 DashScope API Key 后：

**方式 A（推荐）**：在 `src/main/resources/application-local.yml` 写入（该文件已被 `.gitignore` 忽略，不会入库）：

```yaml
spring:
  ai:
    dashscope:
      api-key: sk-你的真实key
```

**方式 B**：设置环境变量（User 级）：

```powershell
[Environment]::SetEnvironmentVariable("AI_DASHSCOPE_API_KEY","sk-你的真实key","User")
```

> 默认启用 `local` profile，会自动加载 `application-local.yml`；无该文件时回退到环境变量。
> ⚠ 切勿把密钥写进入库的 `application.yml`。

### 2. 构建与启动

```powershell
mvn -DskipTests package
java -jar target/novel2screenplay-0.0.1-SNAPSHOT.jar
```

服务默认监听 `http://localhost:8080`。

### 3. 调用接口

`POST /api/screenplay/convert`

| 参数 | 位置 | 默认 | 说明 |
|---|---|---|---|
| 小说全文 | 请求体（text/plain） | — | 必填 |
| `title` | query | 自动生成 | 可选，覆盖自动剧名 |
| `style` | query | `电影` | 改编风格：电影/话剧/短剧/分镜 |
| `format` | query | `yaml` | 输出格式：`yaml` 或 `fountain` |

响应头 `X-Validation-Warnings`：自检修复后残留的问题数（0 表示无残留）。

**curl 示例**（Windows / *nix 通用）：

```bash
curl -X POST "http://localhost:8080/api/screenplay/convert?style=电影&format=yaml" \
     -H "Content-Type: text/plain; charset=utf-8" \
     --data-binary "@src/main/resources/sample/novel.txt" \
     -o screenplay.yml
```

**PowerShell 示例**：

```powershell
$novel = Get-Content -Raw -Encoding UTF8 "src/main/resources/sample/novel.txt"
$bytes = [Text.Encoding]::UTF8.GetBytes($novel)
Invoke-WebRequest "http://localhost:8080/api/screenplay/convert?format=yaml" `
  -Method Post -Body $bytes -ContentType "text/plain; charset=utf-8" `
  -OutFile screenplay.yml
```

---

## 🧪 测试

```powershell
# 离线单元测试（不联网、不烧 token）
mvn test

# 联网 live 测试（真实调用 Qwen，需已配 key）
mvn test -Dlive=true -Dtest=ConversionPipelineLiveTest
```

- 离线测试覆盖：章节切分、YAML/Fountain 导出、全局编号、剧本体检、控制器逻辑、风格映射。
- live 测试覆盖：Qwen 结构化输出、单章抽取、3 章全流程端到端（生成 `examples/sample-output.yml`）。

---

## 📁 示例

- 输入：[`src/main/resources/sample/novel.txt`](src/main/resources/sample/novel.txt)（3 章原创短篇）
- 输出：[`examples/sample-output.yml`](examples/sample-output.yml)（15 个场景、跨章人物合并、来源可追溯）
