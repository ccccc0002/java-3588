# 任务分解看板（四小队并行）v1

## 1. 目标与周期
1. 周期：8-12 周
2. 目标：完成 ZLM + RK3588 并行开放能力并具备灰度发布条件。

## 1.1 实施进展（2026-03-05）
1. 已完成：`/api/camera/list` 推流地址改造（走 `MediaStreamUrlService`，兼容 legacy/zlm）。
2. 已完成：`/stream/play_list`、`/stream/camera_list` 下发完整 `playUrl`。
3. 已完成：`/camera/selectPlay` 下发 `playUrl + videoPort + zlmMode`，`stream/index.ftl` 优先使用后端 `playUrl`。
4. 已完成：`/stream/start`、`/stream/stop` zlm 语义细化（zlm 模式端口可选）。
5. 已完成：上述流控接口补充 `trace_id` 响应字段，便于链路追踪。
6. 已完成：`stream/index_mas.ftl` 改为优先使用后端 `playUrl`。
7. 已完成：`stream/index_tj.ftl`、`stream/index426.ftl` 改为优先使用后端 `playUrl`。
8. 已完成：`/stream/play_list`、`/stream/camera_list`、`/api/camera/list` 增补 `trace_id` 字段。
9. 已完成：新增 `scripts/testing/Validate-Stream-Contracts.ps1`，用于流契约快速回归检查。
10. 已完成：`stream/index.ftlO`、`stream/index_tj.txt` 改为优先使用后端 `playUrl`。
11. 已完成：新增 `scripts/testing/Validate-TraceId-Lists.ps1`，用于列表接口 `trace_id` 回归检查。
12. 已完成：`index.ftl`、`index_tj.ftl`、`index426.ftl`、`index_mas.ftl` 抽取统一 `resolvePlayUrl` 逻辑。
13. 已完成：新增 `scripts/testing/Run-Stream-Validation.ps1` 一键联测脚本（串联流契约与 trace 列表校验）。
14. 已完成：`index.ftlO`、`index_tj.txt` 抽取统一 `resolvePlayUrl` 逻辑。
15. 已完成：`Run-Stream-Validation.ps1` 增加 `quick/full` 模式。
16. 已完成：新增 `InferenceClient` 抽象（`legacy` 与 `rk3588_rknn` 双实现）。
17. 已完成：新增 `InferenceRoutingService`，按 `infer_backend_type` 路由推理后端。
18. 已完成：新增 `/api/inference/health`、`/api/inference/test`，返回统一 `trace_id`。
19. 已完成：新增 `scripts/testing/Validate-Inference-Contracts.ps1` 推理契约校验脚本。
20. 已完成：`Run-Stream-Validation.ps1` 支持 `-IncludeInference` 叠加推理联测。
21. 已完成：新增 `InferenceReportBridgeService`，支持推理结果入库与 websocket 推送闭环。
22. 已完成：新增 `/api/inference/dispatch` 一体化接口（推理 + 可选 `persist_report` 入库）。
23. 已完成：`Validate-Inference-Contracts.ps1` 增补 `/api/inference/dispatch` 契约校验。
24. 已完成：新增 `InferenceIdempotencyService`，按 `trace_id + camera_id + timestamp_ms` 做判重。
25. 已完成：`/api/inference/dispatch` 输出 `idempotent` 状态，重复请求直接短路避免重复入库。
26. 已完成：补齐 `pom.xml`（Spring Boot 2.7.18，JDK 11，Maven 构建链路落地）。
27. 已完成：修复历史乱码造成的编译阻塞（`StreamController`、`ModelServiceImpl`、`CameraServiceImpl` 等）。
28. 已完成：在 RK3588 远程环境 `192.168.1.104` 执行 `mvn -DskipTests compile/package` 双通过。
29. 已完成：新增推理域 TDD 单元测试（`InferenceRoutingService`、`InferenceIdempotencyService`、`InferenceApiController`），共 9 条用例全通过。
30. 已完成：新增 `scripts/testing/Run-Tdd-Gate.ps1` 与 GitHub Actions `tdd-gate` 测试门禁。

## 2. 小队与职责
1. 媒体小队：流媒体接入、播放链路、流状态管理
2. AI小队：RK3588 推理服务、插件化、性能与稳定性
3. 平台后端小队：配置中心、接口改造、编排与治理
4. 前端小队：预览播放、OSD、状态可视化

## 3. 阶段任务（按周）

### Phase 0（W1）契约冻结

#### 媒体小队
1. 输出 ZLM 地址规则文档
2. 提供流可达性检查脚本规范

#### AI小队
1. 输出 RK3588 推理服务 API 草案
2. 提供模型加载/卸载生命周期说明

#### 平台后端小队
1. 输出配置项清单与默认值
2. 输出 legacy/zlm、legacy/rk3588 切换策略

#### 前端小队
1. 输出播放 URL 统一输入规范
2. 输出 OSD 渲染依赖字段清单

### Phase 1（W2-W3）媒体域落地

#### 媒体小队
1. 完成 ZLM 接入与连通验证
2. 完成 StreamUrlResolver（zlm/legacy）实现
3. 完成流状态探活接口

#### 平台后端小队
1. `/stream/play_list`、`/stream/camera_list` 改造为完整 URL 下发
2. `/stream/start`、`/stream/stop` 支持 zlm 语义

#### 前端小队
1. 适配后端完整 `playUrl`
2. 保持 flv.js + OSD 不回归

### Phase 2（W3-W5）AI 域落地

#### AI小队
1. 完成 `/health` 与 `/v1/infer` 最小可用
2. 完成 RKNN + FFmpeg/MPP/RGA 管线打通
3. 提供 1 个基线模型可运行版本

#### 平台后端小队
1. 新增 `InferenceClient` 抽象
2. 完成 `infer_backend_type` 路由切换
3. 对接推理结果入库与 websocket 推送

### Phase 3（W5-W8）联调与治理

#### 媒体小队
1. 多路并发稳定性验证
2. 断流恢复策略验证

#### AI小队
1. 推理超时、重试、熔断策略验证
2. 性能压测与基线报告

#### 平台后端小队
1. trace_id 贯通
2. 幂等去重与死信处理
3. 灰度开关按摄像头分组生效

#### 前端小队
1. 链路状态面板（播放/推理/告警）展示
2. 告警可视化与历史回放联动

### Phase 4（W8-W12）灰度发布

#### 全队共同
1. 分批迁移摄像头
2. 验证回滚剧本
3. 输出生产运维手册

## 4. 里程碑与交付物

### M1（W1 末）
1. PRD v1
2. 接口契约 v1
3. 错误码与事件字典 v1

### M2（W3 末）
1. ZLM 预览链路可用
2. 双模式切换可用

### M3（W5 末）
1. RK3588 推理闭环跑通（至少 1 算法）
2. 告警入库与展示闭环

### M4（W8 末）
1. 可观测与治理能力达标
2. 72h 稳定性通过

### M5（W12 末）
1. 生产灰度完成
2. 开放能力文档 v1 发布

## 5. 依赖与阻塞管理
1. 依赖看板每周二/周五更新。
2. 阻塞超过 24h 升级到架构师 Agent 仲裁。
3. 影响主 KPI 的任务必须附回滚方案。

## 6. Definition of Done（统一门禁）
1. 代码：通过单元测试与集成测试。
2. 契约：通过接口契约测试。
3. 运维：有监控、告警、回滚说明。
4. 文档：更新变更记录与使用说明。

## 7. 风险看板（首批）
1. ZLM 网络连通不稳定
2. RK3588 模型性能不达标
3. 跨队接口漂移
4. 灰度切换导致短时不可用

## 8. 对应负责人占位（待你补充）
1. 媒体小队负责人：`TBD`
2. AI小队负责人：`TBD`
3. 平台后端负责人：`TBD`
4. 前端负责人：`TBD`
5. 架构师 Agent Owner：`TBD`
