<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>账号管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .tip-info { color: #999999; margin-top: 5px; }
        .layui-form-label { width: 140px; }
        .layui-input-block { margin-left: 180px; }
    </style>
</head>
<body>
    <form class="layui-form">
        <div class="mainBox">
            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">新密码</label>
                    <div class="layui-input-block">
                        <input type="text" name="password" autocomplete="off" placeholder="请输入新密码" class="layui-input" value="">
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
            $.post('/account/password', data.field, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    layer.msg('更新成功', {icon:1, time:1000}, function() {
                        parent.layer.close(parent.layer.getFrameIndex(window.name));
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
