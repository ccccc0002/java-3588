<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>${(brandTitle!'AI视频监控管理系统')}</title>
    <link rel="stylesheet" href="/static/component/pear/css/pear.css" />
    <link rel="stylesheet" href="/static/admin/css/other/login.css" />
</head>
<body background="${(loginBackgroundUrl!'/static/admin/images/background.svg')}" style="background-size: cover;">
<form class="layui-form" action="javascript:void(0);">
    <div class="layui-form-item">
        <div class="title">${(brandTitle!'AI视频监控管理系统')}</div>
        <div class="desc" style="margin-bottom: 50px !important;"></div>
    </div>
    <div class="layui-form-item">
        <input type="text" placeholder="账号" lay-verify="required" hover class="layui-input" name="account" value="admin" />
    </div>
    <div class="layui-form-item">
        <input type="password" placeholder="密码" lay-verify="required" hover class="layui-input" name="password" value="" />
    </div>
    <div class="layui-form-item">
        <input type="checkbox" name="" title="记住密码" lay-skin="primary" checked>
    </div>
    <div class="layui-form-item">
        <button type="button" class="pear-btn pear-btn-success login" lay-submit lay-filter="login">
            登录
        </button>
    </div>
</form>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['jquery', 'form', 'button', 'popup'], function() {
        var $ = layui.jquery;
        var form = layui.form;
        var button = layui.button;
        var popup = layui.popup;

        if (window !== top) {
            top.location.href = location.href;
        }

        form.on('submit(login)', function(data) {
            var loading = button.load({
                elem: '.login',
                time: false
            });

            $.post('/login', data.field, function(res) {
                loading.stop();
                if (res.code === 0) {
                    popup.success('登录成功', function() {
                        location.href = '/';
                    });
                } else {
                    popup.failure(res.msg);
                }
            });
            return false;
        });
    });
</script>
</body>
</html>
