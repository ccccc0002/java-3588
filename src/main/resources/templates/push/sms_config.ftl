<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>短信推送配置</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .layui-form-label { width: 130px; }
        .layui-input-block { margin-left: 170px; }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <form class="layui-form" lay-filter="smsPushForm">
                    <div class="layui-form-item">
                        <label class="layui-form-label">启用短信推送</label>
                        <div class="layui-input-block">
                            <input type="checkbox" name="enabled" lay-skin="switch" lay-text="ON|OFF">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">服务商</label>
                        <div class="layui-input-block">
                            <input type="text" name="provider" placeholder="例如：云片/阿里云短信" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">短信API地址</label>
                        <div class="layui-input-block">
                            <input type="text" name="api_url" placeholder="https://sms.yunpian.com/v2/sms/tpl_batch_send.json" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">API Key</label>
                        <div class="layui-input-block">
                            <input type="password" name="api_key" placeholder="请输入短信服务 API Key" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">模板ID</label>
                        <div class="layui-input-block">
                            <input type="text" name="tpl_id" placeholder="请输入模板ID" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <div class="layui-input-block">
                            <button class="pear-btn pear-btn-primary" lay-submit lay-filter="saveSmsPush">保存配置</button>
                            <button type="button" class="pear-btn" id="reloadBtn">重新加载</button>
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
        let layer = layui.layer;
        let canManagePushTargets = false;

        function applyPermissionState() {
            if (canManagePushTargets) {
                $('button[lay-filter=saveSmsPush]').removeClass('layui-btn-disabled').prop('disabled', false);
            } else {
                $('button[lay-filter=saveSmsPush]').addClass('layui-btn-disabled').prop('disabled', true);
            }
        }

        function reloadDetail() {
            $.post('/push/sms-config/detail', {}, function(res) {
                if (res && res.code === 0 && res.data) {
                    form.val('smsPushForm', {
                        enabled: !!res.data.enabled,
                        provider: res.data.provider || '',
                        api_url: res.data.api_url || '',
                        api_key: res.data.api_key || '',
                        tpl_id: res.data.tpl_id || ''
                    });
                    form.render();
                } else {
                    popup.failure((res && res.msg) || '读取配置失败');
                }
            });
        }

        form.on('submit(saveSmsPush)', function(data) {
            if (!canManagePushTargets) {
                popup.failure('无权限操作');
                return false;
            }
            let loading = layer.load(2);
            $.post('/push/sms-config/save', data.field, function(res) {
                if (res && res.code === 0) {
                    layer.msg('保存成功', { icon: 1, time: 1000 });
                    reloadDetail();
                } else {
                    popup.failure((res && res.msg) || '保存失败');
                }
            }).always(function() {
                layer.close(loading);
            });
            return false;
        });

        $('#reloadBtn').on('click', function() {
            reloadDetail();
        });

        $.post('/account/permissions', {}, function(res) {
            if (res && res.code === 0 && res.data) {
                canManagePushTargets = !!res.data.can_manage_push_targets;
            } else {
                canManagePushTargets = false;
            }
            applyPermissionState();
            reloadDetail();
        }).fail(function() {
            canManagePushTargets = false;
            applyPermissionState();
            reloadDetail();
        });
    });
</script>
</body>
</html>

