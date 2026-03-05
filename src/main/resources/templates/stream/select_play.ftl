<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>视频流管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        td .layui-form-select {
            margin-top: -3px;
            margin-left: -15px;
            margin-right: -15px;
        }
        .layui-table-cell {
            overflow: visible !important;
        }

        .layui-table-tool { padding-top: 0 !important; padding-bottom: 0 !important; min-height: 30px !important; }
    </style>
</head>
<body>
<h1 style="font-size: 18px; font-weight: bold; color: #000; text-align: center; padding: 5px 0px;">提示：视频播放最大支持同时9路摄像头</h1>
<div class="layui-row layui-col-space10">
    <div class="layui-col-xs6">
        <div class="layui-card">
            <div class="layui-card-body" style="padding-top: 0; padding-bottom: 0;">
                <table id="cameralist-table" lay-filter="cameralist-table"></table>
            </div>
        </div>
    </div>
    <div class="layui-col-xs6">
        <div class="layui-card">
            <div class="layui-card-body" style="padding-top: 0; padding-bottom: 0;">
                <table id="playlist-table" lay-filter="playlist-table"></table>
            </div>
        </div>
    </div>
</div>

<div class="bottom">
    <div class="button-container">
        <button class="pear-btn pear-btn-sm" id="close-layer">
            <i class="layui-icon layui-icon-close"></i>
            关闭
        </button>
    </div>
</div>
</body>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script type="text/html" id="camera-table-toolbar">
    <span style="font-size: 16px; font-weight: bold; color: #333;">摄像头列表</span>
</script>
<script type="text/html" id="camera-table-actions">
    <a href="#" style="color: #409EFF;" lay-event="saveAndPlay">保存并播放</a>
</script>
<script type="text/html" id="videoUrlTpl">
    <select class="form-control" id="video_port_{{ d.cameraId }}">
        <option value="">请选择</option>
        <#if videoUrls??>
            <#list videoUrls as item>
                <option value="${(item.videoPort)!'0'}">${(item.videoUrl)!'-'}</option>
            </#list>
        </#if>
    </select>
</script>

<#--播放列表-->
<script type="text/html" id="play-table-toolbar">
    <span style="font-size: 16px; font-weight: bold; color: #409EFF;">已选择播放列表</span>
</script>
<script type="text/html" id="play-table-actions">
    <a href="#" style="color: #f43838;" lay-event="stopAndRelease">停止并释放</a>
</script>
<script>
    layui.use(['form', 'jquery', 'popup', 'table'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let table = layui.table;

        //
        let camera_cols = [{
            title: '摄像头名称',
            field: 'cameraName'
        }, {
            title: '已选择播放地址',
            field: 'playUrl',
        }, {
            title: '播放地址',
            field: 'videoUrlTpl',
            templet: '#videoUrlTpl',
        }, {
            title: '操作',
            toolbar: '#camera-table-actions',
            align: 'center',
            width: 110,
            fixed: 'right'
        }];

        //
        table.render({
            elem: '#cameralist-table',
            url: '/stream/camera_list',
            method: 'post',
            page: true,
            cols: [camera_cols],
            skin: 'line',
            height: 'full-148',
            toolbar: '#camera-table-toolbar',
            defaultToolbar: []
        });

        //
        table.on('tool(cameralist-table)', function(obj) {
            if(obj.event == 'saveAndPlay') {
                var load = layer.msg('正在启动播放，请稍等...', {
                    icon:16,
                    shade:[0.1, '#fff'],
                    time:false
                });
                var cameraId = obj.data['cameraId'];
                $.post('/stream/start', {'cameraId': cameraId, 'videoPort': $('#video_port_' + cameraId).val()}, function (res) {
                    layer.closeAll();
                    if(res.code == 0) {
                        table.reload('playlist-table');
                        table.reload('cameralist-table');
                    } else {
                        popup.failure(res.msg);
                    }
                })
            }
        });

        //
        let play_cols = [{
            title: '摄像头名称',
            field: 'cameraName'
        }, {
            title: '播放地址',
            field: 'playUrl'
        }, {
            title: '操作',
            toolbar: '#play-table-actions',
            align: 'center',
            width: 110,
            fixed: 'right'
        }];

        //
        table.render({
            elem: '#playlist-table',
            url: '/stream/play_list',
            method: 'post',
            page: false,
            cols: [play_cols],
            skin: 'line',
            height: 'full-148',
            toolbar: '#play-table-toolbar',
            defaultToolbar: []
        });

        //
        table.on('tool(playlist-table)', function(obj) {
            if(obj.event == 'stopAndRelease') {
                var load = layer.msg('正在停止播放，请稍等...', {
                    icon:16,
                    shade:[0.1, '#fff'],
                    time:false
                });
                $.post('/stream/stop', {'cameraId': obj.data['cameraId']}, function (res) {
                    layer.closeAll();
                    if(res.code == 0) {
                        table.reload('playlist-table');
                        table.reload('cameralist-table');
                    } else {
                        popup.failure(res.msg);
                    }
                })
            }
        });

        $('#close-layer').click(function() {
            parent.layer.close(parent.layer.getFrameIndex(window.name));
        });
    })
</script>
</html>
