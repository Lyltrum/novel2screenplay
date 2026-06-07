# novel2screenplay · AI 小说转剧本工具

将 **3 章以上的小说文本**自动转换为**结构化剧本（YAML）**，让作者快速获得可编辑、可进一步打磨的剧本初稿。

- 输入：一段小说全文（自动识别章节）
- 输出：结构化剧本 YAML（或行业标准 Fountain 格式）
- 定位：降低小说改编门槛，产出"可信、可校对、可手改"的初稿

> Schema 设计与设计原因详见 [`docs/screenplay-schema.md`](docs/screenplay-schema.md)。
> 完整示例输出见 [`docs/斗破苍穹-前十章.yml`](docs/斗破苍穹-前十章.yml)（真实长篇·斗破苍穹前十章实测，剧名由模型自拟《莫欺少年穷》）
> 与 [`examples/sample-output.yml`](examples/sample-output.yml)（内置原创短篇）。

---

## ✨ 亮点

| 亮点 | 说明 |
|---|---|
| **来源可追溯** | 每个场景回填原文出处（章节号 + 原文片段），作者可逐场对照原文校验改编是否忠实——把"AI 黑盒"变成"可验证的辅助初稿" |
| **自检 + 自动修复闭环** | 生成后自动体检（必填字段、对白角色是否在登记表、时间是否中文、来源是否完整），有问题则带着问题清单让模型自我修复（有界 ≤2 轮），残留问题数通过响应头暴露 |
| **跨章人物一致性** | 逐章建立人物登记表（主名 + 别名 + 设定）并合并去重，注入每个场景生成，保证全篇称呼统一（如"铁面人 = 苏窈的兄长"被正确归并） |
| **剧集分集（集≠章）** | 剧集形态下把场景按戏剧节奏重排成「集」（独立叙事单元 + 集尾钩子），模拟编剧分集大纲；自动校验分集"不重不漏"，可指定总集数或交模型按节奏自动决定 |
| **多格式 + 多形态** | 同时支持导出 YAML 与 Fountain；改编形态默认 **剧集**（电视剧/网剧，叠加分集层），可选 电影 / 话剧 / 短剧 / 分镜 单本形态 |
| **长文规模化 + 并行提速** | 超长章节按段落自动切块突破单次上下文限制；**登记与场景抽取按章并行**大幅压缩长篇耗时；跨章连贯靠"前情提要"（各章梗概预先并行算好再拼接），跨章人名一致靠完整人物登记表，提速不降质 |
| **交互式精修** | 作者拿到初稿后，可对单个场景下达修改指令（如"改成外景雨夜"）做局部重生成，保持人物一致、保留来源溯源 |

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
episode/    剧集分集编排       style/      风格指引
prompt/     集中的提示词      pipeline/   流水线编排
controller/ REST 入口         model/      Schema record
config/     Bean 装配
```

流水线顺序：**切章 → 逐章建立完整登记表 → 注入登记表与风格逐章抽场景 → 生成剧名/梗概 → 全局编号组装 → 自检修复闭环 →（剧集形态）按节奏分集 → 导出**。

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
| 小说全文 | 请求体 | — | 必填。两种等价方式：`text/plain` 直接放正文，**或** `multipart/form-data` 上传 `.txt` 文件（字段名 `file`，UTF-8） |
| `title` | query | 自动生成 | 可选，覆盖自动剧名 |
| `style` | query | `剧集` | 改编形态：`剧集`（默认，叠加分集层）/ 电影 / 话剧 / 短剧 / 分镜 |
| `episodes` | query | 自动 | 仅剧集形态生效；指定总集数，缺省由模型按节奏自动决定 |
| `format` | query | `yaml` | 输出格式：`yaml` 或 `fountain` |

响应：
- 响应头 `X-Validation-Warnings`：自检修复后残留的问题数（0 表示无残留）。
- 响应头 `Content-Disposition`：以剧名命名的下载文件（`-OJ`/浏览器可直接存盘）。
- 剧本**末尾附「自检报告」注释**：逐条列出残留告警（`[场景] 字段：说明`），让作者明确知道哪一场、哪个字段还需人工校对——告警不只给数字。注释是 YAML `#` / Fountain `/* */`，不影响格式合法性与 Schema 校验。

出错时返回干净的 JSON 错误 `{status, error}`（不外泄堆栈）：入参为空/格式错误 400；上传过大 413；模型调用全部失败/抽不出场景 502。单章或单块抽取失败会**跳过并告警、不中断整本转换**（尽力而为）。

**流式进度（SSE）**：`POST /api/screenplay/convert/stream`（入参与 `/convert` 完全一致，含文件上传）。长篇转换耗时较长，该端点用 Server-Sent Events 实时推送进度，最后推完整结果：
- `event: progress`，`data: {phase, message}`——逐阶段进展（切分 / 登记 / 抽取每章完成 / 组装 / 自检 / 分集）。
- `event: result`，`data: {format, warnings, filename, body}`——完整剧本与残留告警数。
- `event: error`，`data: {error}`——转换失败原因。

demo 前端默认走此流式端点，右侧实时滚动进度（可直观看到多章**并发**完成）。

**curl 示例**（Windows / *nix 通用）：

```bash
# ① 文本方式：请求体直接放正文，指定分 3 集
curl -X POST "http://localhost:8080/api/screenplay/convert?episodes=3&format=yaml" \
     -H "Content-Type: text/plain; charset=utf-8" \
     --data-binary "@src/main/resources/sample/novel.txt" \
     -o screenplay.yml

# ② 文件上传方式：multipart 上传 .txt（-OJ 让 curl 用响应头里的剧名存盘）
curl -X POST "http://localhost:8080/api/screenplay/convert?episodes=3&format=yaml" \
     -F "file=@src/main/resources/sample/novel.txt" -OJ
```

**PowerShell 示例**：

```powershell
$novel = Get-Content -Raw -Encoding UTF8 "src/main/resources/sample/novel.txt"
$bytes = [Text.Encoding]::UTF8.GetBytes($novel)
Invoke-WebRequest "http://localhost:8080/api/screenplay/convert?format=yaml" `
  -Method Post -Body $bytes -ContentType "text/plain; charset=utf-8" `
  -OutFile screenplay.yml
```

### 交互式精修

`POST /api/screenplay/refine`（JSON 进出）：对单个场景按指令局部重生成，保持人物一致、保留 id 与来源出处。

```json
{
  "scene": { "id": "S1", "heading": {...}, "action": [...], "dialogue": [...], "source": {...} },
  "characters": [ { "name": "沈砚", "aliases": ["沈三郎"], "description": "..." } ],
  "instruction": "把这场改成外景雨夜，并让台词更冷峻"
}
```

返回精修后的 scene（JSON），响应头 `X-Validation-Warnings` 为该场体检残留问题数。

### 长文规模化配置

超长章节会自动按段落切块（块间用滚动提要保连续）。块大小可调：

```yaml
screenplay:
  chunk:
    max-chars: 1500   # 单块最大字符数，按需调整
```

---

## 🧪 测试

```powershell
# 离线单元测试（不联网、不烧 token）
mvn test

# 联网 live 测试（真实调用 Qwen，需已配 key）
mvn test -Dlive=true -Dtest=ConversionPipelineLiveTest
```

- 离线测试覆盖：章节切分、长文分块、YAML/Fountain 导出、全局编号、剧本体检、分集校验（不重不漏）、控制器逻辑、风格映射。
- live 测试覆盖：Qwen 结构化输出、单章抽取、3 章全流程端到端（生成 `examples/sample-output.yml`）。

---

## 🖥️ 可选 demo 前端（非课题要求）

`web/index.html` 是一个独立单页（翻译器风格：左边粘贴小说，右边渲染剧本），仅供直观演示，**不属于课题、可删**。

用法：先启动后端，再用浏览器直接打开 `web/index.html`。支持形态切换（剧集/电影/话剧/短剧/分镜）、剧集形态下指定集数、**选择 .txt 文件上传**（走 multipart）或左侧直接粘贴、视图切换（剧本/YAML/Fountain）、把结果**一键下载**为文件，并显示校验告警数；转换过程**实时滚动进度**（走 SSE 流式端点）。
> 跨域由 `config/WebCorsConfig`（仅 dev、可删）放开。

## 📁 示例

**真实长篇实测（剧集形态）**
- 输入：斗破苍穹 前十章（约 3 万字 / 10 章）——版权测试材料，**未随仓库分发**
- 输出：[`docs/斗破苍穹-前十章.yml`](docs/斗破苍穹-前十章.yml)——10 章 → **74 个场景**、自动分集、跨章人物合并去重（如主角"萧炎"的十余个别名归一）、每场来源可追溯；剧名由模型自拟为《莫欺少年穷》

**可直接跑的内置示例（原创短篇，无版权顾虑）**
- 输入：[`src/main/resources/sample/novel.txt`](src/main/resources/sample/novel.txt)（3 章原创短篇）
- 输出：[`examples/sample-output.yml`](examples/sample-output.yml)（剧集形态：11 个场景、分 3 集带集尾钩子、跨章人物合并、来源可追溯）
