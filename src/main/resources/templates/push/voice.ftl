<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>电话推送配置</title>
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
                <form class="layui-form" lay-filter="voicePushForm">
                    <div class="layui-form-item">
                        <label class="layui-form-label">启用电话推送</label>
                        <div class="layui-input-block">
                            <input type="checkbox" name="enabled" lay-skin="switch" lay-text="ON|OFF">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">服务商</label>
                        <div class="layui-input-block">
                            <input type="text" name="provider" placeholder="例如：阿里云语音/自建网关" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">推送地址</label>
                        <div class="layui-input-block">
                            <input type="text" name="url" placeholder="http://127.0.0.1:9001/voice/push" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">Bearer Token</label>
                        <div class="layui-input-block">
                            <input type="text" name="bearer" placeholder="可选" autocomplete="off" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">号码列表</label>
                        <div class="layui-input-block">
                            <textarea name="numbers" placeholder="多个号码用英文逗号分隔，例如：13800000000,13900000000" class="layui-textarea"></textarea>
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <div class="layui-input-block">
                            <button class="pear-btn pear-btn-primary" lay-submit lay-filter="saveVoicePush">保存配置</button>
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
                $('button[lay-filter=saveVoicePush]').removeClass('layui-btn-disabled').prop('disabled', false);
            } else {
                $('button[lay-filter=saveVoicePush]').addClass('layui-btn-disabled').prop('disabled', true);
            }
        }

        function reloadDetail() {
            $.post('/push/voice/detail', {}, function(res) {
                if (res && res.code === 0 && res.data) {
                    form.val('voicePushForm', {
                        enabled: !!res.data.enabled,
                        provider: res.data.provider || '',
                        url: res.data.url || '',
                        bearer: res.data.bearer || '',
                        numbers: res.data.numbers || ''
                    });
                    form.render();
                } else {
                    popup.failure((res && res.msg) || '读取配置失败');
                }
            });
        }

        form.on('submit(saveVoicePush)', function(data) {
            if (!canManagePushTargets) {
                popup.failure('无权限操作');
                return false;
            }
            let payload = {
                enabled: !!data.field.enabled,
                provider: data.field.provider || '',
                url: data.field.url || '',
                bearer: data.field.bearer || '',
                numbers: data.field.numbers || ''
            };
            let loading = layer.load(2);
            $.post('/push/voice/save', payload, function(res) {
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

