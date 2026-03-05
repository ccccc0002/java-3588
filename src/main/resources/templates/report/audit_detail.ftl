<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>告警审核管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>

    </style>
</head>
<body>
    <div id="wrapper" style="position: relative; background-color: rgba(6,223,223, .1); background-image: url(${imageUrl}); background-repeat: no-repeat; background-size: contain; background-position: center center;">
        <canvas id="demo" style="position: absolute; top: 0; left: 0;"></canvas>
    </div>


    <div class="bottom">
        <div style="float: left; padding-left: 15px;">算法名称： ${(algorithm.name)!''}</div>
        <div class="button-container">
            <button type="submit" class="pear-btn pear-btn-success pear-btn-sm" lay-submit="" lay-filter="ok">
                <i class="layui-icon layui-icon-ok"></i>
                正确
            </button>
            <button type="submit" class="pear-btn pear-btn-danger pear-btn-sm" lay-submit="" lay-filter="fail">
                <i class="layui-icon layui-icon-delete"></i>
                错误
            </button>
            <button class="pear-btn pear-btn-sm" lay-submit="" lay-filter="close">
                <i class="layui-icon layui-icon-close"></i>
                关闭
            </button>
        </div>
    </div>
</body>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['jquery', 'form'], function() {
        let $ = layui.jquery;
        let form = layui.form;
        var params = '${(report.params)!''}';
        var owidth = '${(width)!'1'}';
        var oheight = '${(height)!'1'}';

        window.paintDetect = function(ratio) {
            //
            const canvasEle = document.getElementById('demo');
            const context = canvasEle.getContext('2d');

            //
            if(params != '') {
                var json = JSON.parse(params);
                var len = json.length;
                for(var i = 0; i < len; i++) {
                    var position = json[i]['position'];
                    var confidence = json[i]['confidence'];
                    var type1 = json[i]['type'];
                    // top left point
                    var tlx = parseInt(position[0] * ratio);
                    var tly = parseInt(position[1] * ratio);

                    // top right point
                    var trx = parseInt(position[2] * ratio);
                    var tryz = parseInt(position[1] * ratio);

                    // bottom left point
                    var blx = parseInt(position[0] * ratio);
                    var bly = parseInt(position[3] * ratio);

                    // bottom right point
                    var brx = parseInt(position[2] * ratio);
                    var bry = parseInt(position[3] * ratio);

                    context.beginPath();
                    context.moveTo(tlx, tly);
                    context.lineTo(trx, tryz);
                    context.lineTo(brx, bry);
                    context.lineTo(blx, bly);
                    context.fillStyle = "rgba(245,108,108, .4)";
                    context.fill();
                    context.lineWidth = 2;
                    context.strokeStyle = "#F56C6C";
                    context.closePath();
                    context.stroke();

                    //
                    context.beginPath();
                    context.font = '15px Arial';
                    context.fillStyle = "#ffffff";
                    context.fillText(type1 + ' ' + confidence, tlx + 5, tly + 15);
                    context.closePath();
                }
            }
        }

        //
        $(document).ready(function() {
            var docWidth = $(document).width();
            var docHeight = $(document).height();
            var demoWidth = (docWidth - 0);
            var demoHeight = (docHeight - 50);
            $('#wrapper').css('width', demoWidth + 'px');
            $('#wrapper').css('height', demoHeight + 'px');
            var ratio = (demoHeight / oheight).toFixed(1);
            var imgWidth = parseInt(owidth * ratio);
            var pLeft = parseInt((demoWidth - imgWidth) / 2);
            $('#demo').css('left', pLeft + 'px');
            document.getElementById('demo').width = imgWidth - 5;
            document.getElementById('demo').height = demoHeight - 5;

            window.paintDetect(ratio);
        });

        //
        form.on('submit(ok)', function(data) {
            var loading = layer.load();
            $.post('/report/audit', {'id': '${(report.id)!'0'}', 'result': 1}, function(res) {
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

        //
        form.on('submit(fail)', function(data) {
            var loading = layer.load();
            $.post('/report/audit', {'id': '${(report.id)!'0'}', 'result': 2}, function(res) {
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

        //
        form.on('submit(close)', function(data) {
            parent.layer.close(parent.layer.getFrameIndex(window.name));
            return false;
        });
    })
</script>
</html>
