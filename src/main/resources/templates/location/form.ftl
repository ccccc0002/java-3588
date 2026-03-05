<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>摄像头区域节点管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .layui-form-label { width: 110px; }
        .layui-form-label { width: 140px; }
        .layui-input-block { margin-left: 180px; }
    </style>
</head>
<body>
    <form class="layui-form">
        <input type="hidden" name="id" value="${(id)!''}">
        <input type="hidden" name="parentId" value="${(parentId)!''}">
        <div class="mainBox">
            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">上级区域</label>
                    <div class="layui-input-block">
                        <div class="layui-form-mid layui-word-aux" style="white-space: normal">
                            ${(parentName)!''}
                        </div>
                    </div>
                </div>
            </div>
            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">区域名称</label>
                    <div class="layui-input-block">
                        <input type="text" name="name" lay-verify="required" autocomplete="off" placeholder="请输入区域名称" class="layui-input" value="${(name)!''}">
                    </div>
                </div>
            </div>

            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">经度</label>
                    <div class="layui-input-block">
                        <input type="number" name="longitude" autocomplete="off" placeholder="请输入经度" class="layui-input" value="${(longitude)!''}">
                    </div>
                </div>
            </div>

            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">纬度</label>
                    <div class="layui-input-block">
                        <input type="text" name="latitude" autocomplete="off" placeholder="请输入纬度" class="layui-input" value="${(latitude)!''}">
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
    layui.use(['form', 'jquery', 'popup'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;

        //
        form.on('submit(save)', function(data) {
            var loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/location/save', data.field, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    layer.msg('操作成功', {icon:1, time:1000}, function() {
                        parent.layer.close(parent.layer.getFrameIndex(window.name));
                        parent.createTree();
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
