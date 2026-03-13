<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>Scheduler Config</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-card">
    <div class="layui-card-header">Inference Scheduler Config</div>
    <div class="layui-card-body">
        <form class="layui-form" lay-filter="scheduler-form">
            <div class="layui-form-item">
                <label class="layui-form-label">Enabled</label>
                <div class="layui-input-block">
                    <input type="checkbox" name="enabled" lay-skin="switch" lay-text="ON|OFF">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">Max Cameras</label>
                <div class="layui-input-block">
                    <input type="number" min="1" step="1" name="max_cameras" required lay-verify="required|number"
                           placeholder="e.g. 10" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">Cooldown ms</label>
                <div class="layui-input-block">
                    <input type="number" min="0" step="1" name="cooldown_ms" required lay-verify="required|number"
                           placeholder="e.g. 5000" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">Latency Factor</label>
                <div class="layui-input-block">
                    <input type="number" min="0.1" step="0.1" name="latency_factor" required lay-verify="required|number"
                           placeholder="e.g. 1.0" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">Conc Baseline</label>
                <div class="layui-input-block">
                    <input type="number" min="1" step="1" name="concurrency_baseline" required lay-verify="required|number"
                           placeholder="e.g. 4" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="layui-form-item">
                <div class="layui-input-block">
                    <button class="pear-btn pear-btn-primary" lay-submit lay-filter="saveScheduler">Save</button>
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
                    popup.failure((res && res.msg) || 'Load scheduler config failed');
                    return;
                }
                let data = res.data;
                form.val('scheduler-form', {
                    enabled: data.enabled ? 'on' : '',
                    max_cameras: data.max_cameras,
                    cooldown_ms: data.cooldown_ms,
                    latency_factor: data.latency_factor,
                    concurrency_baseline: data.concurrency_baseline
                });
                form.render();
            });
        }

        form.on('submit(saveScheduler)', function (data) {
            if (!canWriteSystem) {
                popup.failure('permission denied');
                return false;
            }
            let field = data.field || {};
            field.enabled = field.enabled ? '1' : '0';
            let loading = layer.load(2);
            $.post('/config/scheduler/save', field, function (res) {
                layer.close(loading);
                if (res && res.code === 0) {
                    popup.success('Scheduler config saved');
                } else {
                    popup.failure((res && res.msg) || 'Save scheduler config failed');
                }
            }).fail(function () {
                layer.close(loading);
                popup.failure('Save scheduler config failed');
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
