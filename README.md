# 智能体中台知识库管理平台（KMP）

基于 [RuoYi-Vue 3.9.2](https://gitee.com/y_project/RuoYi-Vue) 二次开发的知识库 / 智能问答中台：后端为 Spring Boot 3 的 Maven 多模块工程，前端为 Vue 3 + Element Plus + Vite。

## 功能概览

| 模块 | 能力 |
| --- | --- |
| 系统管理 | 用户、角色、菜单、部门、岗位、字典、参数、通知公告、操作日志、登录日志、在线用户（若依原生能力） |
| 知识单元 | 知识单元（KnowledgeUnit）增删改查、详情、切片预览、批量导入 |
| 知识分组 | 分组（KnowledgeGroup）管理、详情、在线问答测试 |
| 访问授权 | 知识库访问授权（KnowledgeAuth）与调用鉴权 |
| 导入任务 | 导入任务（KnowledgeImportTask）创建、状态轮询、失败重试；后台轮询任务对接外部切片服务与 MinIO |
| 智能问答 | 查询会话（KnowledgeQuerySession）、查询日志（KnowledgeQueryLog）、FAQ 命中日志、SSE 流式回答 |
| 问题簇聚类 | 问题簇（KnowledgeQuestionCluster）与校验任务：Milvus 向量检索 + 千问 embedding / rerank，Elasticsearch 存标准问题 |
| 数据看板 | 问答与 FAQ 日志先写 Redis Stream，再由平台单线程批量落库 |
| 代码生成 | 从数据库表生成 controller / service / mapper / domain / vue 代码 |

## 技术栈

- 后端：Java 17 · Spring Boot 3.5.14 · Spring Security + JWT · MyBatis / MyBatis-Plus 3.5.17 · Druid 1.2.28 · Redis（Lettuce）· Quartz · Spring AI 1.1.8 · Milvus · PageHelper · springdoc-openapi 2.8.17
- 前端：Vue 3.5.26 · Element Plus 2.13.1 · Vite 6.4.1 · Pinia · Vue Router 4 · Axios · ECharts 5 · Sass

## 目录结构

```
agent_platform/
├── kmp-parent/                 后端 Maven 多模块（groupId com.ycy）
│   ├── kmp-admin/              启动模块：RuoYiApplication、系统管理接口、application*.yml
│   ├── kmp-common/             通用工具、注解、领域模型、异常、常量
│   ├── kmp-framework/          安全（JWT/过滤器）、数据源、AOP 切面、Redis 与线程池配置
│   ├── kmp-system/             用户 / 角色 / 菜单 / 部门 / 字典 / 日志 业务
│   ├── kmp-quartz/             定时任务（Quartz）
│   ├── kmp-generator/          代码生成（含 vm 模板）
│   └── kmp-core/               知识库核心业务：知识单元、分组、授权、导入、问答、问题簇
└── kmp-ui/                     前端 Vue3 工程（RuoYi-Vue 3.9.2）
```

## 环境依赖（默认地址见配置文件）

| 组件 | 默认地址 | 用途 |
| --- | --- | --- |
| MySQL | `localhost:3307/kmp` | 业务库（库名 kmp） |
| Redis | `localhost:6379` | 缓存、会话、Redis Stream |
| Milvus | `localhost:19530` | 问题簇向量检索 |
| Elasticsearch | `localhost:9200` | FAQ 标准问题检索（索引 kmp_faq_question） |
| MinIO | `localhost:9000` | 导入文件对象存储 |
| 外部切片服务 | `http://localhost:8000` | 文档切片 / 导入 |
| 外部查询服务 | `http://localhost:8001` | 前端知识库检索 |

## 快速开始

### 后端

```bash
cd kmp-parent
mvn clean package -DskipTests
java -jar kmp-admin/target/kmp-admin.jar
```

也可在 IDEA 中直接运行 `kmp-parent/kmp-admin/src/main/java/com/ycy/RuoYiApplication.java`。

- 服务端口：`8080`（`server.port`），上下文路径 `/`
- 接口文档：`http://localhost:8080/swagger-ui.html`（分组 `default`、`kmp`）
- Druid 控制台：`http://localhost:8080/druid/`
- 数据库表结构请使用若依官方 SQL 脚本按需初始化；本仓库不包含 SQL 备份与初始数据

### 前端

```bash
cd kmp-ui
npm install
npm run dev          # 开发模式，默认 http://localhost:80 ，/dev-api 代理到 http://localhost:8080
npm run build:stage  # 预发打包（.env.staging）
npm run build:prod   # 生产打包（.env.production）
```

## 配置与密钥说明

仓库中**不包含任何真实密钥**：AI 与向量库相关凭据全部通过环境变量注入，配置文件里只保留占位默认值。

| 环境变量 | 用途 | 配置文件中的默认值 |
| --- | --- | --- |
| `OPENAI_API_KEY` | Spring AI 的 OpenAI 兼容接口 key | `xxx`（占位，必须替换） |
| `OPENAI_BASE_URL` | 兼容接口地址 | DashScope compatible-mode |
| `DASHSCOPE_API_KEY` | 千问 embedding / rerank key | `xxx`（占位，必须替换） |
| `DASHSCOPE_API_BASE_URL` | 千问服务地址 | `https://dashscope.aliyuncs.com` |
| `KMP_MILVUS_HOST` / `KMP_MILVUS_PORT` | Milvus 地址 | `localhost` / `19530` |
| `KMP_MILVUS_USERNAME` / `KMP_MILVUS_PASSWORD` | Milvus 账号 | `root` / `milvus`（本地开发默认） |
| `KMP_DB_USERNAME` / `KMP_DB_PASSWORD` | 主库账号 | `root` / `root`（本地开发默认） |
| `KMP_REDIS_PASSWORD` | Redis 口令 | `123123`（本地开发默认） |
| `KMP_TOKEN_SECRET` | JWT 签名密钥 | 若依默认值（**生产必须覆盖**） |
| `DRUID_LOGIN_USERNAME` / `DRUID_LOGIN_PASSWORD` | Druid 控制台账号 | `ruoyi` / `123456`（本地开发默认） |
| `KMP_GENERATOR_DB_PASSWORD` | 代码生成测试类连库口令 | 无（仅 `KmpCodeGenerator` 使用） |

> 表格中的默认值仅为本地开发便利，生产环境请通过环境变量覆盖，尤其是 JWT 密钥、数据库口令与 Druid 控制台口令。请勿把真实 API key 写入配置文件，也不要提交到仓库。

## 许可证

本项目基于若依 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)（MIT）二次开发，遵循 MIT 许可证，保留原作者版权与许可声明，详见 `kmp-parent/LICENSE` 与 `kmp-ui/LICENSE`。