<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>统计配置管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .info { padding: 10px 0px; font-weight: bold; font-size: 16px; text-align: center;}
        .checkbox-list { padding: 15px 30px; }
        .layui-col-sm4 { margin-bottom: 20px; display: flex; flex-direction: row; align-items: center; }
    </style>
</head>
<body class="pear-container1">
<form class="layui-form">
    <div class="info">最多支持勾选8个</div>
    <div class="layui-row checkbox-list">
        <#if algorithmList??>
            <#list algorithmList as item>
            <div class="layui-col-sm4">
                <input type="checkbox" name="statics_flag" value="${(item.id)!''}" lay-skin="primary" title="${(item.name)}" ${(item.staticsFlagVal)}>
            </div>
            </#list>
        </#if>
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
    layui.use(['form', 'jquery', 'util', 'popup'], function() {
        let $ = layui.jquery;
        let popup = layui.popup;
        let form = layui.form;

        //
        form.on('submit(save)', function(data) {
            var loading = layer.load(2, { shade: [0.15,'#000'] });
            var ids = [];
            $('input[type=checkbox]:checked').each(function () {
                ids.push($(this).val());
            });
            $.post('/stream/formConfig', { 'ids': JSON.stringify(ids) }, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    layer.msg('操作成功', {icon:1, time:1000}, function() {
                        parent.handleStaticsTpl();
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
