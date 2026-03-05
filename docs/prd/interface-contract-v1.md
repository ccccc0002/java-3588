# 接口契约清单 v1（ZLM + RK3588 并行开放）

## 1. 文档目的
用于冻结跨小队并行开发的接口契约，避免字段漂移和联调返工。

## 2. 适用范围
1. 媒体域（ZLMediaKit 接入与流地址解析）
2. AI域（RK3588 推理微服务）
3. 平台域（Java 管理后端）
4. 前端域（实时预览 + OSD）

## 3. 版本与变更规则
1. 当前版本：`v1.0.0`
2. 变更级别：
   - Patch：只增不改（新增可选字段）
   - Minor：新增接口或新增必填字段（需兼容策略）
   - Major：删除字段/语义变化（需灰度与回滚方案）
3. 所有变更必须附：影响范围、迁移方案、回滚方案。

## 4. 配置契约（tbl_biz_config）

### 4.1 媒体相关
1. `media_server_type`：`legacy|zlm`
2. `zlm_enable`：`0|1`
3. `zlm_schema`：`http`
4. `zlm_host_public`：前端可达地址
5. `zlm_host_inner`：算法可达地址
6. `zlm_http_port`：默认 `80`
7. `zlm_rtmp_port`：默认 `1935`
8. `zlm_app`：默认 `live`
9. `zlm_play_mode`：`stream|proxy`

### 4.2 推理相关
1. `infer_backend_type`：`legacy|rk3588_rknn`
2. `infer_service_url`：如 `http://127.0.0.1:19080`
3. `infer_timeout_ms`：默认 `3000`
4. `infer_retry_count`：默认 `1`
5. `infer_idempotent_window_ms`：幂等判重窗口（毫秒），默认 `600000`
6. `infer_backend_camera_overrides`：按摄像头灰度覆盖（可选）
  - 允许格式A（map）：`{"100":"rk3588_rknn","101":"legacy"}`
  - 允许格式B（array）：`[{"camera_id":100,"backend_type":"rk3588_rknn"}]`

## 5. 平台 API 契约

### 5.1 `/api/camera/list`（给算法侧）
- 方法：`GET`/`POST`（沿用现状）
- 响应关键字段：
1. `camera_id`：`number`
2. `camera_name`：`string`
3. `rtsp_url`：`string`
4. `video_play`：`0|1`
5. `rtmp_url`：`string`
6. `trace_id`：`string`（同批次响应内一致）

- `zlm` 模式下 `rtmp_url` 规则：
`rtmp://{zlm_host_inner}:{zlm_rtmp_port}/{zlm_app}/{camera_id}`

### 5.2 `/stream/play_list`
- 方法：`POST`
- 响应关键字段：
1. `cameraId`：`number`
2. `cameraName`：`string`
3. `playUrl`：`string`
4. `trace_id`：`string`

- `zlm + stream` 模式：
`http://{zlm_host_public}:{zlm_http_port}/{zlm_app}/{cameraId}.flv`

### 5.3 `/stream/camera_list`
- 方法：`POST`
- 响应关键字段：
1. `cameraId`：`number`
2. `cameraName`：`string`
3. `playUrl`：`string`（未启播可为空字符串）
4. `trace_id`：`string`

### 5.4 `/stream/start`
- 方法：`POST`
- 入参：
1. `cameraId`：`number`（必填）
2. `videoPort`：`number`（legacy 必填，zlm 可忽略）
- 返回：`code/msg` + `data`
1. `trace_id`：`string`
2. `cameraId`：`number`
3. `mode`：`legacy|zlm`
4. `videoPort`：`number`
5. `playUrl`：`string`

### 5.5 `/stream/stop`
- 方法：`POST`
- 入参：
1. `cameraId`：`number`（必填）
- 返回：`code/msg` + `data`
1. `trace_id`：`string`
2. `cameraId`：`number`
3. `mode`：`legacy|zlm`

### 5.6 `/camera/selectPlay`
- 方法：`POST`
- 入参：
1. `cameraId`：`number`（必填）
- 响应 `data` 字段（新增对象结构，兼容旧前端仅取 `videoPort`）：
1. `cameraId`：`number`
2. `videoPort`：`number`
3. `playUrl`：`string`（后端已解析完整播放地址，前端优先使用）
4. `zlmMode`：`0|1`（是否启用 zlm 规则）
5. `trace_id`：`string`

- 行为补充：
1. 当当前摄像头尚未分配播放槽位时，后端会尝试自动分配后再返回地址。

### 5.7 `/api/inference/health`
- 方法：`GET`/`POST`
- 返回：`code/msg` + `data`
1. `trace_id`：`string`
2. `backend_type`：`legacy|rk3588_rknn`
3. `upstream`：`object`（透传推理服务健康探测结果，至少包含 `status`）

### 5.8 `/api/inference/test`
- 方法：`POST`
- 入参（JSON，可选字段均有默认值）：
1. `camera_id`：`number`，默认 `1`
2. `model_id`：`number`，默认 `1`
3. `frame.source`：`string`，默认 `test://frame`
4. `frame.timestamp_ms`：`number`，默认当前时间
5. `roi`：`array`，默认空数组
- 返回：`code/msg` + `data`
1. `trace_id`：`string`
2. `backend_type`：`legacy|rk3588_rknn`（实际路由后端，受全局+灰度配置共同影响）
3. `request`：`object`（平台送检请求体）
4. `result`：`object`（推理结果，包含 `trace_id/camera_id/latency_ms/detections`）

### 5.9 `/api/inference/dispatch`
- 方法：`POST`
- 入参（JSON / Query 混合，核心字段如下）：
1. `trace_id`：`string`（可选，不传则平台自动生成）
2. `camera_id`：`number`
2. `model_id`：`number`
3. `algorithm_id`：`number`（默认回退到 `model_id`）
4. `frame`：`object`
5. `roi`：`array`
6. `persist_report`：`0|1`（默认 `1`，`0` 仅做推理不入库）
- 返回：`code/msg` + `data`
1. `trace_id`：`string`
2. `backend_type`：`legacy|rk3588_rknn`（实际路由后端，受全局+灰度配置共同影响）
3. `request`：`object`
4. `result`：`object`
5. `algorithm_id`：`number`
6. `idempotent`：`object`（`enabled/status/duplicate/key/window_ms/timestamp_ms`）
7. `report`：`object`（`enabled/status/reason/persisted/broadcasted/report_id`）

## 6. 推理微服务契约（RK3588 本地服务）

### 6.1 健康检查
- `GET /health`
- 响应：
```json
{
  "status": "ok",
  "runtime": "rknn",
  "decode": "ffmpeg+mpp+rga",
  "version": "1.0.0"
}
```

### 6.2 推理请求
- `POST /v1/infer`
- 请求体：
```json
{
  "trace_id": "uuid",
  "camera_id": 1001,
  "model_id": 2001,
  "frame": {
    "source": "rtsp://...",
    "timestamp_ms": 1730000000000
  },
  "roi": []
}
```

- 响应体：
```json
{
  "trace_id": "uuid",
  "camera_id": 1001,
  "latency_ms": 38,
  "detections": [
    {
      "label": "person",
      "score": 0.93,
      "bbox": [100, 120, 220, 360]
    }
  ]
}
```

### 6.3 错误码
1. `I4001`：请求参数非法
2. `I4002`：模型不存在或未加载
3. `I5001`：解码失败
4. `I5002`：推理失败
5. `I5003`：内部超时

## 7. 事件契约

### 7.1 websocket `/report/{uid}`
- 类型：`REPORT` / `REPORT_SHOW`
- 必备字段：
1. `id`
2. `cameraId`
3. `algorithmId`
4. `params`（框坐标信息）
5. `createdAt`

### 7.2 事件约束
1. 前端 OSD 仅依赖坐标与 cameraId，不依赖媒体协议。
2. 新增字段必须可向后兼容。

## 8. 非功能契约
1. 所有接口输出必须包含可追踪 `trace_id`（新增链路要求）。
2. 超时约束：平台->推理服务超时不超过 `infer_timeout_ms`。
3. 幂等约束：同一 `trace_id + camera_id + timestamp_ms` 只允许入库一次（在 `dispatch + persist_report=1` 生效）。
4. 灰度约束：当 `infer_backend_camera_overrides` 命中摄像头时，优先使用灰度后端；未命中才回退 `infer_backend_type`。

## 9. 验收检查单
1. 契约测试覆盖所有关键字段。
2. legacy/zlm 双模式均通过回归。
3. 前端播放与 OSD 展示无字段断裂。
4. 推理微服务健康检查、错误码、超时策略可验证。
5. 可执行脚本：
   - `scripts/testing/Validate-Stream-Contracts.ps1`
   - `scripts/testing/Validate-TraceId-Lists.ps1`
   - `scripts/testing/Validate-Inference-Contracts.ps1`
   - `scripts/testing/Run-Stream-Validation.ps1`
6. 一键脚本模式：
   - `-Mode quick`：仅校验流契约（start/stop/selectPlay）
   - `-Mode full`：校验流契约 + trace_id 列表接口
   - `-IncludeInference`：叠加推理契约校验（`/api/inference/health` + `/api/inference/test` + `/api/inference/dispatch`）
7. 推理校验建议参数：
   - `Validate-Inference-Contracts.ps1 -AlgorithmId 1`
   - `Run-Stream-Validation.ps1 -IncludeInference -AlgorithmId 1`
