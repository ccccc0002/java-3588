<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>授权管理</title>
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
                        <label class="layui-form-label">设备唯一ID</label>
                        <div class="layui-input-block">
                            <input type="text" id="device_id" readonly class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">授权密钥</label>
                        <div class="layui-input-block">
                            <input type="text" name="license_key" id="license_key" required lay-verify="required" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">授权路数上限</label>
                        <div class="layui-input-block">
                            <input type="number" min="1" name="max_channels" id="max_channels" required lay-verify="required|number" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">到期日期</label>
                        <div class="layui-input-block">
                            <input type="date" name="expire_at" id="expire_at" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">租户标识</label>
                        <div class="layui-input-block">
                            <input type="text" name="tenant" id="tenant" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">当前摄像头数量</label>
                        <div class="layui-input-block">
                            <input type="text" id="current_camera_count" readonly class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">授权状态</label>
                        <div class="layui-input-block">
                            <div id="license_status" class="layui-form-mid"></div>
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <div class="layui-input-block">
                            <button type="submit" class="pear-btn pear-btn-primary" lay-submit lay-filter="saveLicense">保存</button>
                            <button type="button" class="pear-btn" id="btnReload">刷新</button>
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
                    popup.failure(res.msg || '加载授权信息失败');
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
                    $('#license_status').html('<span class="status-ok">有效</span>');
                } else {
                    $('#license_status').html('<span class="status-bad">无效</span>');
                }
            });
        }

        form.on('submit(saveLicense)', function(data) {
            let loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/config/license/save', data.field, function(res) {
                layer.close(loading);
                if (res.code === 0) {
                    layer.msg('保存成功', {icon: 1, time: 900}, function() {
                        loadLicense();
                    });
                } else {
                    popup.failure(res.msg || '保存失败');
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
