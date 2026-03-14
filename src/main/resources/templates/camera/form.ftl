<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>摄像头管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <#--    <link rel="stylesheet" type="text/css" href="/static/js/webuploader/webuploader.css">-->
    <#--    <link rel="stylesheet" type="text/css" href="/static/js/webuploader/webuploader.custom.css">-->
    <style>
        .tip-info { color: #999999; margin-top: 5px; }
        .layui-form-label { width: 90px; }
        .layui-input-block { margin-left: 130px; }
        .safe-area-wrapper { margin: 0; padding: 12px 18px 0px 10px; }
        .safe-area-bg { margin: 10px 0px; width: 100%; height: 300px; border: 2px solid #1e9fff; position: relative; }
        .canvas-action { position: absolute; width: 0; height: 0; left: 0; top: 0; z-index: 50; }
        .safe-area-btns { display: flex; flex-direction: row; justify-content: space-between; }
        .safe-area-help { font-size: 14px; color: #999999; margin-right: 20px; }
        .dtree-select-show .layui-card-body { padding: 10px 5px; }
        #demoTree2 > li { padding-left: 5px; }
        .algorithm-select-box { margin-bottom: 12px; }
        .algorithm-select-tip { margin-top: 6px; }
    </style>
        <script type="text/html" id="confidenceTpl">
        <input type="number" id="confidence_{{ d.id }}" value="{{ d.confidence }}" min="0.00" step="0.01" style="width: 60px; height: 30px; border: 1px solid #666666;" class="layui-input" />
    </script>

</head>
<body>
<form class="layui-form">
    <input type="hidden" id="id" name="id" value="${(camera.id)!''}">
    <input type="hidden" id="locationId" name="locationId" value="${(camera.locationId)!''}">
    <div class="mainBox">
        <div class="layui-row">
            <div class="layui-col-md5 layui-col-sm5">
                <div class="main-container">
                    <#--                    <div class="layui-form-item">-->
                    <#--                        <label class="layui-form-label">选择基地</label>-->
                    <#--                        <div class="layui-input-block">-->
                    <#--                            <ul id="demoTree2" class="dtree" data-id="0"></ul>-->
                    <#--                        </div>-->
                    <#--                    </div>-->
                    <div class="layui-form-item">
                        <label class="layui-form-label">摄像头名称</label>
                        <div class="layui-input-block">
                            <input type="text" id="name" name="name" lay-verify="required" autocomplete="off" placeholder="请输入摄像头名称" class="layui-input" value="${(camera.name)!''}">
                        </div>
                    </div>

                    <div class="layui-form-item">
                        <label class="layui-form-label">RTSP流地址</label>
                        <div class="layui-input-block">
                            <textarea class="layui-textarea" id="rtspUrl" name="rtspUrl" lay-verify="required" rows="2">${(camera.rtspUrl)!''}</textarea>
                        </div>
                    </div>

                    <div class="layui-form-item">
                        <label class="layui-form-label">告警间隔(秒)</label>
                        <div class="layui-input-block">
                            <input type="number" id="intervalTime" name="intervalTime" lay-verify="required" autocomplete="off" placeholder="请输入告警间隔(秒)" class="layui-input" value="${(camera.intervalTime)!''}">
                        </div>
                    </div>

                    <div class="layui-form-item">
                        <label class="layui-form-label">算法关联</label>
                        <div class="layui-input-block">
                            <div class="algorithm-select-box">
                                <select id="algorithmSelect" xm-select="algorithmSelect" xm-select-search xm-select-skin="normal"></select>
                                <div class="tip-info algorithm-select-tip">支持多选算法；下方可分别设置每个算法的检测置信度</div>
                            </div>
                            <table id="table" lay-filter="table"></table>
                        </div>
                    </div>
                </div>
            </div>
            <div class="layui-col-md7 layui-col-sm7">
                <div class="safe-area-wrapper">
                    <p id="safeAreaText">区域标记</p>
                    <div id="canvas-wrapper" class="safe-area-bg">
                        <canvas id="canvasBg"></canvas>
                        <div id="canvas-action" class="canvas-action">
                            <canvas id="canvasFront"></canvas>
                        </div>
                    </div>
                    <div class="safe-area-btns">
                        <#--                        <div id="uploader">-->
                        <#--                            <div id="fileList"></div>-->
                        <#--                            <div>-->
                        <#--                                <div id="picker" >选择摄像头照片</div>-->
                        <#--                            </div>-->
                        <#--&lt;#&ndash;                            <button type="button" class="pear-btn pear-btn-primary" onclick="window.takePhoto();">拍照</button>&ndash;&gt;-->
                        <#--                        </div>-->

                        <div>
                            <button type="button" class="pear-btn pear-btn-primary" onclick="window.takePhoto();">拍照取图</button>
                            <button type="button" class="pear-btn" onclick="window.updateRtsp();">更新地址</button>
                            <button type="button" class="pear-btn pear-btn-success" onclick="window.addPoint();">新标记</button>
                        </div>
                        <div>
                            <span class="safe-area-help">点击鼠标开始标记</span>
                            <button type="button" class="pear-btn pear-btn-danger" onclick="window.resetSelection();">重置选区</button>
                        </div>
                    </div>
                </div>

                <input type="hidden" name="params" id="params" value="${(camera.params)!''}">
                <input type="hidden" name="fileName" id="fileName" value="${(camera.fileName)!''}">
                <input type="hidden" name="fileWidth" id="fileWidth" value="${(camera.fileWidth)!''}">
                <input type="hidden" name="fileHeight" id="fileHeight" value="${(camera.fileHeight)!''}">
                <input type="hidden" name="canvasWidth" id="canvasWidth" value="${(camera.canvasWidth)!''}">
                <input type="hidden" name="canvasHeight" id="canvasHeight" value="${(camera.canvasHeight)!''}">
                <input type="hidden" name="scaleRatio" id="scaleRatio" value="${(camera.scaleRatio)!''}">
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
<#--<script src="/static/js/jquery.3.6.1.min.js"></script>-->
<#--<script src="/static/js/webuploader/webuploader.min.js"></script>-->
<script>
    layui.use(['form', 'jquery', 'popup', 'table', 'loading', 'dtree', 'select'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let table = layui.table;
        let loading = layui.loading;
        let dtree = layui.dtree;
        let formSelect = layui.select;

        //
        var multiPoints = [];
        const canvasEle = document.getElementById('canvasFront');
        const context = canvasEle.getContext('2d');
        let startPosition = { x: 0, y: 0 };
        var points = [];
        var updatePoint = false;
        var fileName = '${(camera.fileName)!''}';
        var apiParams = '${(camera.apiParams)!''}';
        if(apiParams != '') {
            multiPoints = JSON.parse(apiParams);
        }

        //
        <#--var Dtree = dtree.render({-->
        <#--    elem: "#demoTree2",-->
        <#--    line: true,-->
        <#--    ficon: ["1", "-1"],-->
        <#--    icon: ["0", "5"],-->
        <#--    initLevel: "1",-->
        <#--    method: 'post',-->
        <#--    url: "/warehouse/listTree",-->
        <#--    select: true,-->
        <#--    done: function (data, url, first) {-->
        <#--        var warehouse_id = '${(camera.wareHouseId)!''}';-->
        <#--        if(warehouse_id != '') {-->
        <#--            dtree.selectVal(Dtree, warehouse_id);-->
        <#--        }-->
        <#--    }-->
        <#--});-->

        //
        let cols = [{
            title: '算法名称',
            field: 'name'
        }, {
            title: '置信度',
            field: 'confidence',
            templet: '#confidenceTpl'
        }];

        var algorithmRows = [];
        window.syncAlgorithmConfidenceCache = function() {
            var rowLen = algorithmRows.length;
            for(var i = 0; i < rowLen; i++) {
                var input = $('#confidence_' + algorithmRows[i].id);
                if(input.length > 0) {
                    algorithmRows[i].confidence = input.val();
                }
            }
        }

        window.getSelectedAlgorithms = function() {
            return formSelect.value('algorithmSelect', 'val') || [];
        }

        window.renderAlgorithmTable = function() {
            window.syncAlgorithmConfidenceCache();
            var selectedMap = new Map();
            var selectedAlgorithms = window.getSelectedAlgorithms();
            for(var i = 0; i < selectedAlgorithms.length; i++) {
                selectedMap.set(String(selectedAlgorithms[i]), true);
            }
            var renderRows = [];
            for(var j = 0; j < algorithmRows.length; j++) {
                var row = algorithmRows[j];
                if(selectedMap.has(String(row.id))) {
                    renderRows.push(row);
                }
            }
            table.render({
                elem: '#table',
                id: 'cameraAlgorithmTable',
                data: renderRows,
                page: false,
                cols: [cols],
                skin: 'line',
                toolbar: false,
                defaultToolbar: [],
                text: {
                    none: '请选择至少一个算法'
                }
            });
        }

        $.post('/camera/algorithm/listData?cameraId=${(camera.id)!''}', function(res) {
            algorithmRows = (res && res.data) ? res.data : [];
            var options = [];
            var selectedIds = [];
            for(var i = 0; i < algorithmRows.length; i++) {
                var row = algorithmRows[i];
                var selected = row.checked ? ' selected' : '';
                if(row.checked) {
                    selectedIds.push(String(row.id));
                }
                options.push('<option value="' + row.id + '"' + selected + '>' + row.name + '</option>');
            }
            $('#algorithmSelect').html(options.join(''));
            formSelect.render('algorithmSelect', {
                showCount: 3,
                init: selectedIds
            });
            formSelect.on('algorithmSelect', function() {
                window.renderAlgorithmTable();
            });
            window.renderAlgorithmTable();
        });
        //
        form.on('submit(save)', function(data) {
            window.syncAlgorithmConfidenceCache();
            var algorithms = window.getSelectedAlgorithms();

            if(algorithms.length == 0) {
                popup.failure("请至少选择一个算法");
                return false;
            }

            if(points.length > 0 && points.length < 3) {
                popup.failure("区域标记错误，请至少选择3个节点");
                return false;
            }

            if(points.length > 0 && ($('#fileName').val() == '')) {
                popup.failure("请在区域标记前拍照取图");
                return false;
            }

            // algorithms -> confidence
            var confidenceMap = new Map();
            for(var i = 0; i < algorithmRows.length; i++) {
                confidenceMap.set(String(algorithmRows[i].id), algorithmRows[i].confidence);
            }
            var confidences = [];
            var aLen = algorithms.length;
            for(var i = 0; i < aLen; i++) {
                var algoId = String(algorithms[i]);
                var cv = confidenceMap.get(algoId);
                if($.trim(cv) != '') {
                    confidences.push(cv);
                }
            }

            if(confidences.length != algorithms.length) {
                popup.failure("算法置信度不能为空");
                return false;
            }

            //
            if(points.length > 0) {
                if(multiPoints == null) {
                    multiPoints = [];
                }
                multiPoints.push(points);
                updatePoint = true;
            }

            //
            var dataJSON = {
                'id': $('#id').val(),
                'locationId': $('#locationId').val(),
                'name': $('#name').val(),
                'rtspUrl': $('#rtspUrl').val(),
                'intervalTime': $('#intervalTime').val(),
                'algorithmvos': algorithms.join(','),
                'params': ((multiPoints != null && multiPoints.length > 0) ? JSON.stringify(multiPoints) : ''),
                'fileName': $('#fileName').val(),
                'fileWidth': $('#fileWidth').val(),
                'fileHeight': $('#fileHeight').val(),
                'canvasWidth': $('#canvasWidth').val(),
                'canvasHeight': $('#canvasHeight').val(),
                'scaleRatio': $('#scaleRatio').val(),
                'confidencevos': confidences.join(','),
                'updatePoint': (updatePoint ? 1 : 0)

            }

            var loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/camera/save', dataJSON, function(res) {
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
        window.addPoint = function() {
            if(points == null || points.length == 0) {
                return false;
            }

            if(points.length > 0 && points.length < 3) {
                popup.failure("当前标记点少于3个");
                return false;
            }

            if(points.length > 0 && ($('#fileName').val() == '')) {
                popup.failure("标记前请拍照取图");
                return false;
            }

            multiPoints.push(points);
            points = [];
            updatePoint = true;
            popup.success("可以开始新的标记");
        }

        window.takePhoto = function() {
            loading.block({
                type: 5,
                elem: '.safe-area-bg',
                msg: ''
            });
            $.post('/camera/takePhoto', {'rtspUrl': $('#rtspUrl').val()}, function(res) {
                if(res.code == 0) {
                    popup.success('拍照完成');
                    $('#fileName').val(res.data);
                    window.resetSelection();
                    window.loadImage(res.data);
                } else {
                    popup.failure(res.msg);
                }
                loading.blockRemove(".safe-area-bg", 1000);
            })
        }


        // var uploader = WebUploader.create({
        //     pick: {
        //         id: '#picker',
        //         label: '选择摄像头照片'
        //     },
        //     compress: false,
        //     auto: true,
        //     swf: '/static/js/webuploader/Uploader.swf',
        //     server: '/image/upload',
        //     accept: {
        //         title: 'Images',
        //         extensions: 'jpg,jpeg,png',
        //         mimeTypes: 'image/jpg, image/jpeg, image/png'
        //     }
        // });
        //
        // uploader.on('uploadSuccess', function(file, response) {
        //     if(response.code == 0) {
        //         popup.success('摄像头照片上传完成');
        //         $('#fileName').val(response.data);
        //         window.resetSelection();
        //         window.loadImage(response.data);
        //     } else {
        //         popup.failure(response.msg);
        //     }
        // });
        //
        // uploader.on('uploadError', function(file) {
        //     popup.failure('摄像头照片上传失败，请尝试重新操作.');
        // });
        //
        // uploader.on('uploadComplete', function() {
        //     uploader.reset();
        // });

        window.loadImage = function(fileName) {
            if(fileName == null || fileName == '') {
                return ;
            }
            var w = parseInt($('#canvas-wrapper').width());
            var canvas = document.getElementById('canvasBg');
            var ctx = canvas.getContext('2d');
            var img = new Image();
            img.onload = function () {
                var h = parseInt(img.height * w / img.width);
                $('#canvas-wrapper').css('height', h + 'px');
                $('#canvas-action').css('width', w + 'px').css('height', h + 'px');
                document.getElementById("canvasBg").width = w;
                document.getElementById("canvasBg").height = h;
                document.getElementById("canvasFront").width = w;
                document.getElementById("canvasFront").height = h;

                //
                //if(multiPoints == null || multiPoints.length == 0) {
                $('#fileWidth').val(img.width);
                $('#fileHeight').val(img.height);
                $('#canvasWidth').val(w);
                $('#canvasHeight').val(h);
                // $('#scaleRatio').val((img.width / w).toFixed(2));
                $('#scaleRatio').val((img.width / w).toFixed(1));
                //}

                //
                ctx.drawImage(img, 0, 0, w, h);

                //
                if(multiPoints != null && multiPoints.length > 0) {
                    var ratio = (w / img.width).toFixed(2);
                    var tmMultiPoints = [];
                    var mLen = multiPoints.length;
                    for (var i = 0; i < mLen; i++) {
                        context.beginPath();
                        var tmPoint = multiPoints[i];
                        var tmLen = tmPoint.length;
                        var newPoint = [];
                        for (var j = 0; j < tmLen; j++) {
                            // newPoint.push({
                            //     x: parseInt(tmPoint[j].x * ratio),
                            //     y: parseInt(tmPoint[j].y * ratio)
                            // });

                            newPoint.push({
                                x: Math.ceil(tmPoint[j].x * ratio),
                                y: Math.ceil(tmPoint[j].y * ratio)
                            });


                        }
                        tmMultiPoints.push(newPoint);
                        for (var j = 0; j < tmLen; j++) {
                            if (j == 0) {
                                context.moveTo(newPoint[0].x, newPoint[0].y);
                            } else {
                                context.lineTo(newPoint[j].x, newPoint[j].y);
                            }
                        }
                        context.fillStyle = "rgba(64, 158, 255, .4)";
                        context.fill();
                        context.lineWidth = 2;
                        context.strokeStyle = "#409EFF";
                        context.closePath();
                        context.stroke();

                        for (var j = 0; j < tmLen; j++) {
                            window.drawCircle(newPoint[j].x, newPoint[j].y);
                        }
                        //window.drawCircle(parseInt(startPosition.x), parseInt(startPosition.y));
                    }
                    multiPoints = tmMultiPoints;
                }
            }
            img.src = '/image/stream?fileName=' + fileName;
        }

        //
        window.getClientOffset = function(event) {
            var sLeft = $('#canvas-wrapper').offset().left;
            var sTop = $('#canvas-wrapper').offset().top;
            const { pageX, pageY } = event.touches ? event.touches[0] : event;
            const x = pageX - sLeft;
            const y = pageY - sTop;
            return {
                x,
                y
            }
        }

        //
        window.drawCircle = function(x, y) {
            context.beginPath();
            context.arc(x, y, 4, 0, 2*Math.PI);
            // context.closePath();
            context.strokeStyle = "#409EFF";
            context.fillStyle = "#409EFF";
            context.fill();
            context.closePath();
            context.stroke();
        }

        //
        window.clearCanvas = function() {
            context.clearRect(0, 0, canvasEle.width, canvasEle.height);
        }

        //
        window.mouseDownListener = function(event) {
            startPosition = window.getClientOffset(event);
            window.clearCanvas();
            var len = points.length;
            if(len == 0) {
                // window.drawCircle(parseInt(startPosition.x), parseInt(startPosition.y));
                window.drawCircle(Math.ceil(startPosition.x), Math.ceil(startPosition.y));
            } else if(len == 1) {
                context.beginPath();
                context.moveTo(points[0].x, points[0].y);
                context.lineTo(startPosition.x, startPosition.y);
                context.fillStyle = "rgba(64, 158, 255, .4)";
                context.fill();
                context.lineWidth = 2;
                context.strokeStyle = "#409EFF";
                context.closePath();
                context.stroke();
                window.drawCircle(points[0].x, points[0].y);
                // window.drawCircle(parseInt(startPosition.x), parseInt(startPosition.y));
                window.drawCircle(Math.ceil(startPosition.x), Math.ceil(startPosition.y));
            } else {
                context.beginPath();
                for(var i = 0; i < len; i++) {
                    if(i == 0) {
                        context.moveTo(points[0].x, points[0].y);
                    } else {
                        context.lineTo(points[i].x, points[i].y);
                    }
                }
                // context.lineTo(parseInt(startPosition.x), parseInt(startPosition.y));
                context.lineTo(Math.ceil(startPosition.x), Math.ceil(startPosition.y));
                context.fillStyle = "rgba(64, 158, 255, .4)";
                context.fill();
                context.lineWidth = 2;
                context.strokeStyle = "#409EFF";
                context.closePath();
                context.stroke();
                for(var i = 0; i < len; i++) {
                    window.drawCircle(points[i].x, points[i].y);
                }
                // window.drawCircle(parseInt(startPosition.x), parseInt(startPosition.y));
                window.drawCircle(Math.ceil(startPosition.x), Math.ceil(startPosition.y));
            }
            // points.push({
            //     x: parseInt(startPosition.x),
            //     y: parseInt(startPosition.y)
            // });

            points.push({
                x: Math.ceil(startPosition.x),
                y: Math.ceil(startPosition.y)
            });

            //
            if(multiPoints == null) {
                multiPoints = [];
            }
            var mLen = multiPoints.length;
            for(var i = 0; i < mLen; i++) {
                context.beginPath();

                var tmPoint = multiPoints[i];
                var tmLen = tmPoint.length;
                for(var j = 0; j < tmLen; j++) {
                    if(j == 0) {
                        context.moveTo(tmPoint[0].x, tmPoint[0].y);
                    } else {
                        context.lineTo(tmPoint[j].x, tmPoint[j].y);
                    }
                }
                context.fillStyle = "rgba(64, 158, 255, .3)";
                context.fill();
                context.lineWidth = 2;
                context.strokeStyle = "#409EFF";
                context.closePath();
                context.stroke();
                for(var j = 0; j < tmLen; j++) {
                    window.drawCircle(tmPoint[j].x, tmPoint[j].y);
                }
                //window.drawCircle(parseInt(startPosition.x), parseInt(startPosition.y));
            }
        }

        //
        window.resetSelection = function() {
            window.clearCanvas();
            points = [];
            multiPoints = [];
            updatePoint = true;
            popup.success('选区已重置');
        }

        //
        window.updateRtsp = function() {
            loading.block({
                type: 5,
                elem: '.safe-area-bg',
                msg: ''
            });
            $.post('/camera/updateRtsp', {'id': $('#id').val()}, function(res) {
                if(res.code == 0) {
                    popup.success('地址已更新');
                    $('#rtspUrl').val(res.data);
                } else {
                    popup.failure(res.msg);
                }
                loading.blockRemove(".safe-area-bg", 1000);
            })
        }

        //
        $('#canvasFront').on('mousedown', window.mouseDownListener);
        $('#close-layer').click(function() {
            parent.layer.close(parent.layer.getFrameIndex(window.name));
        });

        //
        window.loadImage(fileName);
    })
</script>
</html>
