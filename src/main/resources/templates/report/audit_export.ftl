<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>审核导出</title>
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
                    <label class="layui-form-label">摄像头</label>
                    <div class="layui-input-block">
                        <select name="cameraId" id="cameraId" class="layui-select">
                            <option value="" selected>全部</option>
                            <#list cameraList as item>
                                <option value="${(item.id)!''}">${(item.name)!''}</option>
                            </#list>
                        </select>
                    </div>
                </div>
                <div class="layui-form-item">
                    <label class="layui-form-label">算法</label>
                    <div class="layui-input-block">
                        <select name="algorithmId" id="algorithmId" class="layui-select">
                            <option value="" selected>全部</option>
                            <#list algorithmList as item>
                                <option value="${(item.id)!''}">${(item.name)!''}</option>
                            </#list>
                        </select>
                    </div>
                </div>
                <div class="layui-form-item">
                    <label class="layui-form-label">开始时间</label>
                    <div class="layui-input-block">
                        <input type="text" name="startText" id="startText" readonly autocomplete="off" placeholder="请输入开始时间" class="layui-input" value="">
                    </div>
                </div>
                <div class="layui-form-item">
                    <label class="layui-form-label">结束时间</label>
                    <div class="layui-input-block">
                        <input type="text" name="endText" id="endText" readonly autocomplete="off" placeholder="请输入结束时间" class="layui-input" value="">
                    </div>
                </div>
                <div class="layui-form-item">
                    <label class="layui-form-label">审核状态</label>
                    <div class="layui-input-block">
                        <input type="radio" name="auditState" value="0" title="全部" checked>
                        <input type="radio" name="auditState" value="1" title="已审核">
                        <input type="radio" name="auditState" value="2" title="待审核">
                    </div>
                </div>
                <div class="layui-form-item">
                    <label class="layui-form-label"></label>
                    <div class="layui-input-block">
                        <span style="color: red;">导出数据请根据条件进行控制，因为需要把图片复制到指定目录才能打包，所以要考虑硬盘是否有充足的空间</span>
                    </div>
                </div>
            </div>
        </div>


        <div class="bottom">
            <div class="button-container">
                <button type="submit" class="pear-btn pear-btn-primary pear-btn-sm" lay-submit="" lay-filter="save">
                    <i class="layui-icon layui-icon-ok"></i>
                    导出
                </button>
                <button class="pear-btn pear-btn-sm" id="closeBtn">
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

        laydate.render({
            elem: '#startText',
            format: 'yyyy-MM-dd'
        });

        laydate.render({
            elem: '#endText',
            format: 'yyyy-MM-dd'
        });

        //
        form.on('submit(save)', function(data) {
            var loading = layer.load();
            $.post('/report/audit/export', data.field, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    layer.msg('操作成功', {icon:1, time:1000}, function() {
                        $("<form action='/report/audit/download' method='get' style='display: none'><input type='text' name='fileName' value='" + res.data + "'/></form>").appendTo('body').submit().remove();
                    });
                } else {
                    popup.failure(res.msg);
                }
            });
            return false;
        });

        //
        $(document).ready(function() {
            $('#closeBtn').on('click', function() {
                parent.layer.close(parent.layer.getFrameIndex(window.name));
            })
        });
    })
</script>
</html>
