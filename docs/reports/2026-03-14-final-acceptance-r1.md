# 最终验收联调报告（R1）

- 时间：2026-03-14
- 环境：RK3588（192.168.1.104）
- 目标：完成二期收尾的最终联调验收，覆盖 UI+接口链路、H.264/H.265、推理与资源门禁。

## 验收矩阵

1. Final Nextwave 严格回归（3轮，失败即停）
2. Phase11 H.264 Handoff（含质量诊断 + 告警预览 + 资源门禁）
3. Phase12 H.265 Closeout（含 soak + 质量诊断 + 告警预览 + 资源门禁）

## 执行命令

```bash
# 1) nextwave 严格回归
bash scripts/rk3588/Run-Phase3-Nextwave-Loop.sh \
  --iterations 3 \
  --poll-interval-sec 10 \
  --session-timeout-sec 1200 \
  --keep-latest 4 \
  --output-dir runtime/test-out/final/final-acceptance-nextwave-r1

# 2) phase11 h264 handoff
bash scripts/rk3588/Run-Phase11-Handoff.sh \
  --base-url http://127.0.0.1:18082 \
  --runtime-api-url http://127.0.0.1:18081 \
  --bridge-url http://127.0.0.1:19080 \
  --bootstrap-token edge-demo-bootstrap \
  --plugin-id yolov8n \
  --camera-id 1 --model-id 1 --algorithm-id 1 \
  --source rtsp://admin:Admin123@192.168.1.245:554/h264/ch1/main/av_stream \
  --timeout-sec 45 \
  --runtime-stack-budget 12 \
  --expect-snapshot-telemetry-status ok \
  --expect-plan-telemetry-status ok \
  --expect-bridge-decode-runtime-status ok \
  --expect-bridge-decode-mode mpp-rga \
  --max-plan-concurrency-pressure 1 \
  --max-plan-suggested-min-dispatch-ms 2000 \
  --stage-retry-attempts 1 \
  --runtime-stack-retry-attempts 2 \
  --verify-quality-diagnostics \
  --quality-iterations 8 \
  --quality-interval-ms 250 \
  --quality-timeout-sec 45 \
  --verify-alarm-preview \
  --alarm-preview-timeout-sec 45 \
  --soak-duration-sec 120 \
  --soak-interval-sec 5 \
  --soak-max-iterations 2 \
  --soak-max-failed-steps 1 \
  --max-memory-used-delta-mb 1500 \
  --max-loadavg-1m 25 \
  --output-dir runtime/test-out/final/phase11-h264-r1

# 3) phase12 h265 closeout
bash scripts/rk3588/Run-Phase12-H265-Closeout.sh \
  --base-url http://127.0.0.1:18082 \
  --runtime-api-url http://127.0.0.1:18081 \
  --bridge-url http://127.0.0.1:19080 \
  --bootstrap-token edge-demo-bootstrap \
  --plugin-id yolov8n \
  --camera-id 1 --model-id 1 --algorithm-id 1 \
  --source rtsp://admin:Admin123@192.168.1.245:554/h265/ch1/main/av_stream \
  --timeout-sec 45 \
  --runtime-stack-budget 12 \
  --expect-snapshot-telemetry-status ok \
  --expect-plan-telemetry-status ok \
  --expect-bridge-decode-runtime-status ok \
  --expect-bridge-decode-mode mpp-rga \
  --max-plan-concurrency-pressure 2 \
  --max-plan-suggested-min-dispatch-ms 3200 \
  --stage-retry-attempts 1 \
  --runtime-stack-retry-attempts 2 \
  --soak-duration-sec 180 \
  --soak-interval-sec 5 \
  --soak-max-iterations 3 \
  --soak-max-failed-steps 1 \
  --quality-iterations 10 \
  --quality-interval-ms 250 \
  --quality-timeout-sec 45 \
  --verify-alarm-preview \
  --alarm-preview-timeout-sec 45 \
  --max-memory-used-delta-mb 2000 \
  --max-loadavg-1m 25 \
  --output-dir runtime/test-out/final/phase12-h265-r1
```

## 结果

1. Final Nextwave 严格回归：`passed`（3/3）
2. Phase11 H.264 Handoff：`passed`
3. Phase12 H.265 Closeout：`passed`

## 关键指标摘录

1. H.264 质量诊断：8/8 成功，失败 0，`status=passed`
2. H.265 质量诊断：10/10 成功，失败 0，`status=passed`
3. 资源门禁：
   - H.264：`memory_used_delta_mb=-18.77`，`loadavg_1m=0.68`，门禁通过
   - H.265：`memory_used_delta_mb=-6.68`，`loadavg_1m=1.08`，门禁通过
4. 解码模式门禁：`expect-bridge-decode-mode=mpp-rga` 通过（H.264/H.265 均通过）

## 产物路径

1. `runtime/test-out/final/final-acceptance-nextwave-r1/latest.json`
2. `runtime/test-out/final/phase11-h264-r1/summary.json`
3. `runtime/test-out/final/phase12-h265-r1/summary.json`

## 结论

本轮最终验收联调（R1）通过，可进入交付确认与后续小项优化阶段。
