<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>时段管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .layui-form-label { width: 110px; }
        .layui-form-label { width: 140px; }
        .layui-input-block { margin-left: 180px; }

        /* 设置只展示时分，隐藏秒那一列 */
        .laydate-time-list {
            padding-bottom: 0;
            overflow: hidden;
        }

        .laydate-time-list > li {
            width: 50% !important;
        }

        .laydate-time-list > li:last-child {
            display: none;
        }

        .laydate-time-list ol li {
            width: 100% !important;
            padding-left: 0 !important;
            text-align: center !important;
        }
    </style>
</head>
<body>
    <form class="layui-form">
        <input type="hidden" name="id" value="${(reportPeriod.id)!''}">
        <input type="hidden" name="cameraId" value="${(cameraId)!''}">
        <div class="mainBox">
            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">选择算法</label>
                    <div class="layui-input-block">
                        <select name="algorithmId" id="algorithmId" class="layui-select">
                            <option value="" selected>-请选择-</option>
                            <#list algorithmList as item>
                                <option value="${(item.id)!''}">${(item.name)!''}</option>
                            </#list>
                        </select>
                    </div>
                </div>
                <div class="layui-form-item">
                    <label class="layui-form-label">开始时点</label>
                    <div class="layui-input-block">
                        <input type="text" name="startText" id="startText" lay-verify="required" readonly autocomplete="off" placeholder="请输入开始时点" class="layui-input" value="${(reportPeriod.startText)!''}">
                    </div>
                </div>
                <div class="layui-form-item">
                    <label class="layui-form-label">结束时点</label>
                    <div class="layui-input-block">
                        <input type="text" name="endText" id="endText" lay-verify="required" readonly autocomplete="off" placeholder="请输入结束时点" class="layui-input" value="${(reportPeriod.endText)!''}">
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
    layui.use(['form', 'jquery', 'popup', 'laydate'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let laydate = layui.laydate;

        var algorithmId = '${(reportPeriod.algorithmId)!''}';
        $('#algorithmId').val(algorithmId);
        form.render('select');

        laydate.render({
            elem: '#startText',
            type: 'time',
            format: 'HH:mm'
        });

        laydate.render({
            elem: '#endText',
            type: 'time',
            format: 'HH:mm'
        });

        //
        form.on('submit(save)', function(data) {
            var loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/report/period/save', data.field, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    layer.msg('操作成功', {icon:1, time:1000}, function() {
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
