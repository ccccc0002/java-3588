<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>视频流管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .tip-info { color: #999999; margin-top: 5px; }
    </style>
</head>
<body>
<form class="layui-form">
    <input type="hidden" id="id" name="id" value="${id!''}">
    <div class="mainBox">
        <div class="main-container">
            <div class="layui-form-item">
                <label class="layui-form-label">摄像头</label>
                <div class="layui-input-block">
                    <select class="layui-input" name="cameraId">
                        <option value="" selected>-Select-</option>
                        <#list cameraList as item>
                            <option value="${(item.id)!''}">${(item.name)!''}</option>
                        </#list>
                    </select>
                </div>
            </div>
        </div>
    </div>

    <div class="bottom">
        <div class="button-container">
            <button type="submit" class="pear-btn pear-btn-primary pear-btn-sm" lay-submit="" lay-filter="save">
                <i class="layui-icon layui-icon-ok"></i>
                选择
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
    layui.use(['form', 'jquery', 'popup', 'table'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let table = layui.table;

        //
        form.on('submit(save)', function(data) {
            parent.selectRtsp(data.field.id, data.field.cameraId);
            parent.layer.close(parent.layer.getFrameIndex(window.name));
            return false;
        });

        $('#close-layer').click(function() {
            parent.layer.close(parent.layer.getFrameIndex(window.name));
        });
    })
</script>
</html>
