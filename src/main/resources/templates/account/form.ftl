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
        <input type="hidden" name="id" value="${(account.id)!''}">
        <div class="mainBox">
            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">员工姓名</label>
                    <div class="layui-input-block">
                        <input type="text" name="name" lay-verify="required" autocomplete="off" placeholder="请输入员工姓名" class="layui-input" value="${(account.name)!''}">
                    </div>
                </div>

                <div class="layui-form-item">
                    <label class="layui-form-label">登录账号</label>
                    <div class="layui-input-block">
                        <input type="text" name="account" lay-verify="required" autocomplete="off" placeholder="请输入登录账号" class="layui-input" value="${(account.account)!''}">
                    </div>
                </div>

                <#if !(account.id)??>
                    <div class="layui-form-item">
                        <label class="layui-form-label">账号密码</label>
                        <div class="layui-input-block">
                            <input type="text" name="password" lay-verify="required" autocomplete="off" placeholder="请输入初始密码" class="layui-input" value="">
                            <div class="tip-info">初始密码</div>
                        </div>
                    </div>
                </#if>

                <div class="layui-form-item">
                    <label class="layui-form-label">账号状态</label>
                    <div class="layui-input-block">
                        <input type="radio" name="state" value="0" title="正常" checked>
                        <input type="radio" name="state" value="1" title="禁用">
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
        var state = '${(account.state)!'0'}';
        $('input[name=state]').each(function() {
            if($(this).val() == state) {
                $(this).prop('checked', true);
            }
        });

        //
        form.on('submit(save)', function(data) {
            var loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/account/save', data.field, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    layer.msg('Success', {icon:1, time:1000}, function() {
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
