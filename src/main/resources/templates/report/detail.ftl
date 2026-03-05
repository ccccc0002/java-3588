<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>告警管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <link href="/static/js/image-zoom/index.css" rel="stylesheet" />
    <style>
        .layui-form-label { width: 100px; }
        .layui-input-block { margin-left: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        #canvasBox { position: absolute; left: 5px; right: 5px; top: 5px; bottom: 5px; }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <div class="layui-row layui-col-space10">
                    <div class="layui-col-md4">
<#--                        <div href="#" style="float: right; width: 50px; height: 15px; border: 0px solid red;" ondblclick="window.doDiscard();"></div>-->
                        <form class="layui-form" action="javascript:void(0);" style="margin: 0 auto; padding-top: 40px;">
                            <div class="layui-form-item">
                                <label class="layui-form-label">摄像头名称:</label>
                                <div class="layui-input-block">
                                    <div class="layui-form-mid layui-word-aux">${(camera.name)!''}</div>
                                </div>
                            </div>
                            <div class="layui-form-item">
                                <label class="layui-form-label">摄像头RTSP:</label>
                                <div class="layui-input-block" style="white-space: normal">
                                    <div class="layui-form-mid layui-word-aux" style="">${(camera.rtspUrl)!''}</div>
                                </div>
                            </div>
                            <div class="layui-form-item">
                                <label class="layui-form-label">告警间隔 (秒):</label>
                                <div class="layui-input-block">
                                    <div class="layui-form-mid layui-word-aux">${(camera.intervalTime)!''}</div>
                                </div>
                            </div>
                            <div class="layui-form-item">
                                <label class="layui-form-label">算法名称:</label>
                                <div class="layui-input-block">
                                    <div class="layui-form-mid layui-word-aux">${(algorithm.name)!''}</div>
                                </div>
                            </div>
                            <div class="layui-form-item">
                                <label class="layui-form-label">告警时间:</label>
                                <div class="layui-input-block">
                                    <div class="layui-form-mid layui-word-aux">
                                        ${(report.createdStr)!''}
                                    </div>
                                </div>
                            </div>
                            <div class="layui-form-item">
                                <label class="layui-form-label">图片地址:</label>
                                <div class="layui-input-block">
                                    <div class="layui-form-mid layui-word-aux" style="white-space: normal">
                                        ${(webUrl)!''}/report/stream?id=${(report.id)!'0'}
                                    </div>
                                </div
                            </div>
                        </form>
                    </div>
                </div>
                    <div class="layui-col-md8" style="position: relative;">
                        <#if (report.fileName)?? && report.fileName != "" >
                            <a href="javascript:void(0)" data-magnify="gallery" data-group="g1" data-src="/report/stream?id=${(report.id)!'0'}">
                                <img id="cameraImg" src="/report/stream?id=${(report.id)!'0'}" style="width: 100%; height: 100%;">
                            </a>
<#--                            <img id="cameraImg" src="/report/stream?id=${(report.id)!'0'}" style="width: 100%; height: 100%;">-->
<#--                            <div id="canvasBox">-->
<#--                                <canvas id="canvasPaint"></canvas>-->
<#--                            </div>-->
                        </#if>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/image-zoom/index.js"></script>
<script src="/static/js/jquery.rotate.min.js"></script>
<script>
    layui.use(['jquery'], function() {
        let $ = layui.jquery;
        var algorithmName = '${(algorithm.name)!''}';
        var params = '${(report.params)!''}';
        var owidth = '${(width)!'1'}';

        window.paintDetect = function() {
            var w = $('#cameraImg').width();
            var h = $('#cameraImg').height();
            document.getElementById('canvasPaint').width = w - 5;
            document.getElementById('canvasPaint').height = h - 5;

            //
            const canvasEle = document.getElementById('canvasPaint');
            const context = canvasEle.getContext('2d');

            //
            var ratio = (w / owidth).toFixed(2);

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
                    context.font = '12px Arial';
                    context.fillStyle = "#409EFF";
                    context.fillText(type1 + ' ' + confidence, tlx + 5, tly + 10);
                    context.closePath();
                }
            }

            //
            if(algorithmName == 'forbidden-area') {
                var apiparams = '${(camera.apiParams)!''}';
                if(apiparams != '') {
                    context.beginPath();
                    var json = JSON.parse(apiparams);
                    var len = json.length;
                    if(len >= 3) {
                        for(var i = 0; i < len; i++) {
                            var position = json[i];
                            var pX = parseInt(position['x'] * ratio);
                            var pY = parseInt(position['y'] * ratio);
                            if(i == 0) {
                                context.moveTo(pX, pY);
                            } else {
                                context.lineTo(pX, pY);
                            }
                        }

                        context.fillStyle = "rgba(103,194,58, .4)";
                        context.fill();
                        context.lineWidth = 2;
                        context.strokeStyle = "#67C23A";
                        context.closePath();
                        context.stroke();
                    }
                }
            }
        }

        //
        $("#cameraImg").on("load", function() {
            setTimeout(function() {
                window.paintDetect();
            }, 300);
        }).each(function() {
            if(this.complete) $(this).trigger('load');
        });
    })

    $(function () {
        const bodyWidth = document.body.clientWidth;
        const bodyHeight = document.body.clientHeight;
        const bodyBottom = document.documentElement.clientHeight - document.body.getBoundingClientRect().bottom;
        const modelWidth=bodyWidth-100;
        const modelHeight=bodyHeight+bodyBottom-100;

        $('[data-magnify]').Magnify({
            Toolbar: [
                'rotateLeft',
                'rotateRight',
                'zoomIn',
                'actualSize',
                'zoomOut'
            ],
            keyboard:true,
            draggable:true,
            movable:true,
            modalSize:[modelWidth,modelHeight],
            beforeOpen:function (obj,data) {
                console.log('beforeOpen')
            },
            opened:function (obj,data) {
                console.log('opened')
            },
            beforeClose:function (obj,data) {
                console.log('beforeClose')
            },
            closed:function (obj,data) {
                console.log('closed')
            },
            beforeChange:function (obj,data) {
                console.log('beforeChange')
            },
            changed:function (obj,data) {
                console.log('changed')
            }
        });

    })
</script>
</html>
