<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>模型测试</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <link href="/static/js/webuploader/webuploader.css?v=1.0" rel="stylesheet" />
    <style>
        .upload-zone {
            width: 100%;
            min-height: 260px;
            border: 1px dashed #dcdfe6;
            border-radius: 8px;
            background: #fafafa;
            display: flex;
            align-items: center;
            justify-content: center;
            position: relative;
            overflow: hidden;
        }
        .upload-tip {
            color: #666;
            font-size: 14px;
            text-align: center;
        }
        .preview-wrap {
            width: 100%;
            min-height: 240px;
            border: 1px solid #ebeef5;
            border-radius: 6px;
            background: #fff;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
            position: relative;
        }
        .preview-wrap img {
            max-width: 100%;
            max-height: 360px;
            display: block;
        }
        .overlay-canvas {
            position: absolute;
            top: 0;
            left: 0;
            cursor: crosshair;
        }
        .result-grid {
            display: grid;
            grid-template-columns: 1fr;
            gap: 12px;
        }
        @media (min-width: 1200px) {
            .result-grid {
                grid-template-columns: 1fr 1fr;
            }
        }
        .detection-table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 8px;
            font-size: 12px;
        }
        .detection-table th,
        .detection-table td {
            border: 1px solid #ebeef5;
            padding: 6px;
            text-align: left;
            vertical-align: top;
        }
        .section-title {
            font-size: 14px;
            font-weight: 600;
            color: #333;
            margin: 8px 0;
        }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md5">
        <div class="layui-card">
            <div class="layui-card-body">
                <form class="layui-form">
                    <input type="hidden" id="fileName" />

                    <div class="layui-form-item">
                        <label class="layui-form-label">图片</label>
                        <div class="layui-input-block">
                            <div id="drop-zone" class="upload-zone">
                                <div class="upload-tip" id="drop-tip">拖拽图片到这里或点击下方按钮选择</div>
                                <div id="roi-image-container" style="width:100%;height:100%;position:relative;"></div>
                            </div>
                            <div style="margin-top:10px;">
                                <button type="button" id="picker" class="pear-btn pear-btn-primary pear-btn-sm">选择图片</button>
                            </div>
                        </div>
                    </div>

                    <div class="layui-form-item">
                        <label class="layui-form-label">算法</label>
                        <div class="layui-input-block">
                            <div class="layui-row layui-col-space5">
                                <#if algorithmList??>
                                    <#list algorithmList as item>
                                        <div class="layui-col-xs6 layui-col-sm4 layui-col-md6 layui-col-lg4">
                                            <input type="checkbox" name="algorithms" value="${(item.id)!''}" lay-skin="primary" title="${(item.name)!''}">
                                        </div>
                                    </#list>
                                </#if>
                            </div>
                        </div>
                    </div>

                    <div class="layui-form-item">
                        <label class="layui-form-label">ROI 点位</label>
                        <div class="layui-input-block" style="display:flex;gap:8px;">
                            <input type="text" id="marks" name="marks" class="layui-input" placeholder="点击图片生成多边形点位 JSON">
                            <button type="button" class="pear-btn pear-btn-primary pear-btn-sm" id="reset-roi">重置</button>
                        </div>
                    </div>

                    <div class="layui-form-item">
                        <label class="layui-form-label">摄像头ID</label>
                        <div class="layui-input-block">
                            <input type="text" id="cameraId" name="cameraId" class="layui-input" placeholder="可选，不填则自动生成">
                        </div>
                    </div>

                    <div class="layui-form-item">
                        <label class="layui-form-label">RTSP抓拍</label>
                        <div class="layui-input-block" style="display:flex;gap:8px;">
                            <input type="text" id="captureRtspUrl" class="layui-input" placeholder="rtsp://user:pass@ip:554/xxx">
                            <button type="button" class="pear-btn pear-btn-primary pear-btn-sm" id="capture-frame-btn">抓拍一帧</button>
                        </div>
                    </div>

                    <div class="layui-form-item" style="text-align:center;">
                        <button class="pear-btn pear-btn-primary pear-btn-md" lay-submit lay-filter="predict-submit">
                            <i class="layui-icon layui-icon-search"></i>测试
                        </button>
                        <button type="reset" class="pear-btn pear-btn-md" style="margin-left:8px;">重置</button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <div class="layui-col-md7">
        <div class="layui-card">
            <div class="layui-card-body">
                <div class="result-grid">
                    <div>
                        <div class="section-title">原图</div>
                        <div class="preview-wrap" id="origin-preview"></div>
                    </div>
                    <div>
                        <div class="section-title">标注图</div>
                        <div class="preview-wrap" id="annotated-preview"></div>
                    </div>
                </div>

                <div class="section-title" style="margin-top:12px;">检测结果列表</div>
                <table class="detection-table">
                    <thead>
                    <tr>
                        <th>#</th>
                        <th>类别</th>
                        <th>置信度</th>
                        <th>框坐标</th>
                        <th>算法</th>
                    </tr>
                    </thead>
                    <tbody id="detection-body">
                    <tr><td colspan="5">暂无结果</td></tr>
                    </tbody>
                </table>

                <div class="section-title" style="margin-top:12px;">原始返回</div>
                <pre id="json_result" style="white-space:pre-wrap;word-break:break-word;min-height:120px;"></pre>
            </div>
        </div>
    </div>
</div>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/webuploader/md5.js"></script>
<script src="/static/js/webuploader/webuploader.min.js"></script>
<script>
    layui.use(['form', 'jquery', 'popup'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;

        let uploader = null;
        let marks = [];
        let ctx = null;

        function clearDetections() {
            $('#detection-body').html('<tr><td colspan="5">暂无结果</td></tr>');
            $('#json_result').text('');
            $('#annotated-preview').html('');
        }

        function safeParse(text, fallback) {
            if (!text) {
                return fallback;
            }
            try {
                return JSON.parse(text);
            } catch (e) {
                return fallback;
            }
        }

        function getClientOffset(event) {
            let area = $('#roi-image-container');
            let sLeft = area.offset().left;
            let sTop = area.offset().top;
            let pageX = event.pageX;
            let pageY = event.pageY;
            return { x: pageX - sLeft, y: pageY - sTop };
        }

        function drawPoint(x, y) {
            ctx.beginPath();
            ctx.arc(x, y, 4, 0, 2 * Math.PI);
            ctx.fillStyle = '#409EFF';
            ctx.fill();
            ctx.closePath();
        }

        function redrawPolygon() {
            if (!ctx) {
                return;
            }
            let canvas = document.getElementById('roi-canvas');
            if (!canvas) {
                return;
            }
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            if (marks.length === 0) {
                return;
            }
            ctx.beginPath();
            for (let i = 0; i < marks.length; i++) {
                let p = marks[i];
                if (i === 0) {
                    ctx.moveTo(p.x, p.y);
                } else {
                    ctx.lineTo(p.x, p.y);
                }
            }
            ctx.closePath();
            ctx.fillStyle = 'rgba(64, 158, 255, .25)';
            ctx.strokeStyle = '#409EFF';
            ctx.lineWidth = 2;
            ctx.fill();
            ctx.stroke();
            for (let i = 0; i < marks.length; i++) {
                drawPoint(marks[i].x, marks[i].y);
            }
        }

        function bindCanvasEvents() {
            $('#roi-canvas').off('mousedown').on('mousedown', function(event) {
                let clickPoint = getClientOffset(event);
                marks.push({ x: Math.round(clickPoint.x), y: Math.round(clickPoint.y) });
                $('#marks').val(JSON.stringify(marks));
                redrawPolygon();
            });
        }

        function renderSourceImage(filePath) {
            let src = '/model/test/stream?file=' + encodeURIComponent(filePath) + '&_=' + Date.now();
            $('#drop-tip').hide();
            $('#roi-image-container').html('<img id="roi-image" src="' + src + '" style="width:100%;display:block;" />');
            $('#origin-preview').html('<img id="origin-image" src="' + src + '" />');
            clearDetections();

            $('#roi-image').on('load', function() {
                let img = this;
                let pW = $(img).width();
                let pH = $(img).height();
                $('#roi-image-container').append('<canvas id="roi-canvas" class="overlay-canvas" width="' + pW + '" height="' + pH + '"></canvas>');
                ctx = document.getElementById('roi-canvas').getContext('2d');
                marks = [];
                $('#marks').val('');
                bindCanvasEvents();
            });
        }

        function renderDetections(list) {
            if (!Array.isArray(list) || list.length === 0) {
                $('#detection-body').html('<tr><td colspan="5">未检测到目标</td></tr>');
                return;
            }
            let html = '';
            for (let i = 0; i < list.length; i++) {
                let it = list[i] || {};
                let type = it.type || '-';
                let confidence = (it.confidence === undefined || it.confidence === null) ? '-' : Number(it.confidence).toFixed(3);
                let position = Array.isArray(it.position) ? JSON.stringify(it.position) : '-';
                let algorithmName = it.algorithm_name || (it.algorithm_id ? ('#' + it.algorithm_id) : '-');
                html += '<tr>'
                    + '<td>' + (i + 1) + '</td>'
                    + '<td>' + type + '</td>'
                    + '<td>' + confidence + '</td>'
                    + '<td>' + position + '</td>'
                    + '<td>' + algorithmName + '</td>'
                    + '</tr>';
            }
            $('#detection-body').html(html);
        }

        function createUploader() {
            if (uploader) {
                uploader.destroy();
                uploader = null;
            }
            uploader = WebUploader.create({
                auto: true,
                swf: '/static/js/webuploader/Uploader.swf',
                server: '/model/test/upload',
                pick: '#picker',
                dnd: '#drop-zone',
                resize: false,
                accept: {
                    title: 'Images',
                    extensions: 'jpg,jpeg,bmp,png',
                    mimeTypes: 'image/*'
                }
            });

            uploader.on('uploadSuccess', function(file, res) {
                if (!res || res.code !== 0) {
                    popup.failure((res && res.msg) || '上传失败');
                    return;
                }
                $('#fileName').val(res.data);
                renderSourceImage(res.data);
            });

            uploader.on('uploadError', function() {
                popup.failure('上传失败');
            });

            uploader.on('uploadComplete', function() {
                uploader.reset();
            });
        }

        $('#reset-roi').on('click', function() {
            marks = [];
            $('#marks').val('');
            redrawPolygon();
        });

        $('#capture-frame-btn').on('click', function() {
            let rtspUrl = ($('#captureRtspUrl').val() || '').trim();
            if (!rtspUrl) {
                popup.failure('请输入 RTSP 地址');
                return;
            }
            let loading = layer.load(2);
            $.post('/model/test/capture', { rtspUrl: rtspUrl }, function(res) {
                layer.close(loading);
                if (!res || res.code !== 0) {
                    popup.failure((res && res.msg) || '抓拍失败');
                    return;
                }
                $('#fileName').val(res.data);
                renderSourceImage(res.data);
                popup.success('抓拍成功');
            });
        });

        form.on('submit(predict-submit)', function() {
            if (!$('#fileName').val()) {
                popup.failure('请先上传测试图片');
                return false;
            }

            let algorithms = [];
            $('input[name="algorithms"]').each(function() {
                if ($(this).prop('checked')) {
                    algorithms.push($(this).val());
                }
            });
            if (algorithms.length === 0) {
                popup.failure('请选择至少一个算法');
                return false;
            }

            let payload = {
                file: $('#fileName').val(),
                algorithms: JSON.stringify(algorithms),
                marks: $('#marks').val(),
                cameraId: $('#cameraId').val(),
                imgHeight: $('#roi-image').height() || 0
            };

            let loading = layer.load(2);
            $.post('/model/test/predict', payload, function(res) {
                layer.close(loading);
                if (!res || res.code !== 0) {
                    popup.failure((res && res.msg) || '测试失败');
                    return;
                }

                let data = res.data || {};
                let detections = data.detections;
                if (!Array.isArray(detections)) {
                    detections = safeParse(data.json, []);
                }
                renderDetections(detections);
                $('#json_result').text(data.rawJson || data.json || '');

                if (data.resultFile) {
                    let resultSrc = '/model/test/stream?file=' + encodeURIComponent(data.resultFile) + '&_=' + Date.now();
                    $('#annotated-preview').html('<img src="' + resultSrc + '" />');
                } else {
                    $('#annotated-preview').html('<div style="color:#999;">暂无标注图</div>');
                }
            });
            return false;
        });

        $(function() {
            createUploader();
        });
    });
</script>
</body>
</html>
