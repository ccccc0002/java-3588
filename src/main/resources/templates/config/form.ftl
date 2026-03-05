<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>系统配置</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .tip-info { color: #999999; margin-top: 5px; }
        .layui-form-label { width: 140px; }
        .layui-input-block { margin-left: 180px; }
    </style>
</head>
<body>
    <form class="layui-form">
        <input type="hidden" name="id" value="${(config.id)!''}">
        <div class="mainBox">
            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">配置名称</label>
                    <div class="layui-input-block">
                        <input type="text" name="name" lay-verify="required" autocomplete="off" placeholder="请输入配置名称" class="layui-input" value="${(config.name)!''}">
                    </div>
                </div>

                <div class="layui-form-item">
                    <label class="layui-form-label">配置标识</label>
                    <div class="layui-input-block">
                        <input type="text" name="tag" lay-verify="required" autocomplete="off" placeholder="请输入配置标识" class="layui-input" value="${(config.tag)!''}">
                    </div>
                </div>

                <div class="layui-form-item">
                    <label class="layui-form-label">配置值</label>
                    <div class="layui-input-block">
                        <input type="text" name="val" lay-verify="required" autocomplete="off" placeholder="请输入配置值" class="layui-input" value="${(config.val)!''}">
                    </div>
                </div>
            </div>
        </div>

        <div class="bottom">
            <div class="button-container">
                <button type="submit" class="pear-btn pear-btn-primary pear-btn-sm" lay-submit="" lay-filter="save">
                    <i class="layui-icon layui-icon-ok"></i>
                    提交
                </button>
                <button class="pear-btn pear-btn-sm" id="close-layer">
                    <i class="layui-icon layui-icon-close"></i>
                    关闭
                </button>
            </div>
        </div>
    </form>
</body>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['form', 'jquery', 'popup', 'laytpl'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let laytpl = layui.laytpl;

        //
        form.on('submit(save)', function(data) {
            var loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/config/save', data.field, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    layer.msg('提示', {icon:1, time:1000}, function() {
                        parent.layer.close(parent.layer.getFrameIndex(window.name));
                        parent.layui.table.reload('table');
                    });
                } else {
                    popup.failure(res.msg);
                }
            });
            return false;
        });

        $('#close-layer').click(function() {
            parent.layer.close(parent.layer.getFrameIndex(window.name));
        });
    })
</script>
</html>
