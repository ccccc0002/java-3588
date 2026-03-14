<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>调度配置</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-card">
    <div class="layui-card-header">推理调度配置</div>
    <div class="layui-card-body">
        <form class="layui-form" lay-filter="scheduler-form">
            <div class="layui-form-item">
                <label class="layui-form-label">启用调度</label>
                <div class="layui-input-block">
                    <input type="checkbox" name="enabled" lay-skin="switch" lay-text="开启|关闭">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">最大摄像头数</label>
                <div class="layui-input-block">
                    <input type="number" min="1" step="1" name="max_cameras" required lay-verify="required|number"
                           placeholder="例如：10" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">冷却时间(ms)</label>
                <div class="layui-input-block">
                    <input type="number" min="0" step="1" name="cooldown_ms" required lay-verify="required|number"
                           placeholder="例如：5000" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">延迟系数</label>
                <div class="layui-input-block">
                    <input type="number" min="0.1" step="0.1" name="latency_factor" required lay-verify="required|number"
                           placeholder="例如：1.0" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">并发基线</label>
                <div class="layui-input-block">
                    <input type="number" min="1" step="1" name="concurrency_baseline" required lay-verify="required|number"
                           placeholder="例如：4" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">最大工作线程数</label>
                <div class="layui-input-block">
                    <input type="number" min="1" step="1" name="max_workers" required lay-verify="required|number"
                           placeholder="例如：3" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <div class="layui-input-block">
                    <button class="pear-btn pear-btn-primary" lay-submit lay-filter="saveScheduler">保存</button>
                </div>
            </div>
        </form>
    </div>
</div>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['form', 'jquery', 'popup'], function () {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let canWriteSystem = false;

        function setWritable(writable) {
            canWriteSystem = !!writable;
            if (canWriteSystem) {
                return;
            }
            $('input,button').prop('disabled', true);
            form.render();
        }

        function loadConfig() {
            $.post('/config/scheduler/info', {}, function (res) {
                if (!res || res.code !== 0 || !res.data) {
                    popup.failure((res && res.msg) || '加载调度配置失败');
                    return;
                }
                let data = res.data;
                form.val('scheduler-form', {
                    enabled: data.enabled ? 'on' : '',
                    max_cameras: data.max_cameras,
                    cooldown_ms: data.cooldown_ms,
                    latency_factor: data.latency_factor,
                    concurrency_baseline: data.concurrency_baseline,
                    max_workers: data.max_workers
                });
                form.render();
            });
        }

        form.on('submit(saveScheduler)', function (data) {
            if (!canWriteSystem) {
                popup.failure('无权限操作');
                return false;
            }
            let field = data.field || {};
            field.enabled = field.enabled ? '1' : '0';
            let loading = layer.load(2);
            $.post('/config/scheduler/save', field, function (res) {
                layer.close(loading);
                if (res && res.code === 0) {
                    popup.success('调度配置保存成功');
                } else {
                    popup.failure((res && res.msg) || '保存调度配置失败');
                }
            }).fail(function () {
                layer.close(loading);
                popup.failure('保存调度配置失败');
            });
            return false;
        });

        $.post('/account/permissions', {}, function (res) {
            if (res && res.code === 0 && res.data) {
                setWritable(!!res.data.can_write_system);
            } else {
                setWritable(false);
            }
        }).always(function () {
            loadConfig();
        });
    });
</script>
</body>
</html>
