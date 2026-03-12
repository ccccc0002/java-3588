<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Account</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
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
                <label class="layui-form-label">Name</label>
                <div class="layui-input-block">
                    <input type="text" name="name" lay-verify="required" autocomplete="off" placeholder="Name" class="layui-input" value="${(account.name)!''}">
                </div>
            </div>

            <div class="layui-form-item">
                <label class="layui-form-label">Account</label>
                <div class="layui-input-block">
                    <input type="text" name="account" lay-verify="required" autocomplete="off" placeholder="Account" class="layui-input" value="${(account.account)!''}">
                </div>
            </div>

            <#if !(account.id)??>
                <div class="layui-form-item">
                    <label class="layui-form-label">Password</label>
                    <div class="layui-input-block">
                        <input type="text" name="password" lay-verify="required" autocomplete="off" placeholder="Initial password" class="layui-input" value="">
                    </div>
                </div>
            </#if>

            <div class="layui-form-item">
                <label class="layui-form-label">Role</label>
                <div class="layui-input-block">
                    <select name="role" id="role">
                        <option value="super_admin">super_admin</option>
                        <option value="ops">ops</option>
                        <option value="read_only">read_only</option>
                    </select>
                </div>
            </div>

            <div class="layui-form-item">
                <label class="layui-form-label">State</label>
                <div class="layui-input-block">
                    <input type="radio" name="state" value="0" title="Enabled" checked>
                    <input type="radio" name="state" value="1" title="Disabled">
                </div>
            </div>
        </div>
    </div>

    <div class="bottom">
        <div class="button-container">
            <button type="submit" class="pear-btn pear-btn-primary pear-btn-sm" lay-submit lay-filter="save">
                <i class="layui-icon layui-icon-ok"></i>
                Submit
            </button>
            <button class="pear-btn pear-btn-sm" id="close-layer">
                <i class="layui-icon layui-icon-close"></i>
                Close
            </button>
        </div>
    </div>
</form>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['form', 'jquery', 'popup'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;

        let state = '${(account.state)!'0'}';
        $('input[name=state]').each(function() {
            if ($(this).val() === state) {
                $(this).prop('checked', true);
            }
        });
        $('#role').val('${(accountRole)!'ops'}');
        form.render();

        form.on('submit(save)', function(data) {
            let loading = layer.load(2, { shade: [0.15, '#000'] });
            $.post('/account/save', data.field, function(res) {
                layer.close(loading);
                if (res.code === 0) {
                    layer.msg('Success', {icon: 1, time: 900}, function() {
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
            return false;
        });
    });
</script>
</body>
</html>

