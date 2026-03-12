<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>License</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .layui-form-label { width: 160px; }
        .layui-input-block { margin-left: 190px; }
        .status-ok { color: #16b777; }
        .status-bad { color: #ff5722; }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space12">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <form class="layui-form" id="licenseForm">
                    <div class="layui-form-item">
                        <label class="layui-form-label">Device ID</label>
                        <div class="layui-input-block">
                            <input type="text" id="device_id" readonly class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">License Key</label>
                        <div class="layui-input-block">
                            <input type="text" name="license_key" id="license_key" required lay-verify="required" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">Max Channels</label>
                        <div class="layui-input-block">
                            <input type="number" min="1" name="max_channels" id="max_channels" required lay-verify="required|number" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">Expire Date</label>
                        <div class="layui-input-block">
                            <input type="date" name="expire_at" id="expire_at" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">Tenant</label>
                        <div class="layui-input-block">
                            <input type="text" name="tenant" id="tenant" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">Current Cameras</label>
                        <div class="layui-input-block">
                            <input type="text" id="current_camera_count" readonly class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">Status</label>
                        <div class="layui-input-block">
                            <div id="license_status" class="layui-form-mid"></div>
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <div class="layui-input-block">
                            <button type="submit" class="pear-btn pear-btn-primary" lay-submit lay-filter="saveLicense">Save</button>
                            <button type="button" class="pear-btn" id="btnReload">Reload</button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['form', 'jquery', 'popup'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;

        function loadLicense() {
            $.post('/config/license/info', function(res) {
                if (res.code !== 0) {
                    popup.failure(res.msg || 'Load license failed');
                    return;
                }
                let d = res.data || {};
                $('#device_id').val(d.device_id || '');
                $('#license_key').val(d.license_key || '');
                $('#max_channels').val(d.max_channels || '');
                $('#expire_at').val(d.expire_at || '');
                $('#tenant').val(d.tenant || '');
                $('#current_camera_count').val(d.current_camera_count || 0);
                if (d.valid) {
                    $('#license_status').html('<span class="status-ok">VALID</span>');
                } else {
                    $('#license_status').html('<span class="status-bad">INVALID</span>');
                }
            });
        }

        form.on('submit(saveLicense)', function(data) {
            let loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/config/license/save', data.field, function(res) {
                layer.close(loading);
                if (res.code === 0) {
                    layer.msg('Saved', {icon: 1, time: 900}, function() {
                        loadLicense();
                    });
                } else {
                    popup.failure(res.msg || 'Save failed');
                }
            });
            return false;
        });

        $('#btnReload').on('click', loadLicense);
        loadLicense();
    });
</script>
</body>
</html>

