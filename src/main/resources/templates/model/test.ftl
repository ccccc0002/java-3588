<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>模型测试</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <link href="/static/js/webuploader/webuploader.css?v=1.0" rel="stylesheet" />
    <style>
        .xbox { position: absolute; border: 2px solid #f43838; background-color: rgba(255, 0, 0, .2); font-size: 12px; color: #1e9fff; }
        .xcvs { position: absolute; top: 0; left: 0; }
        .maskUpload{
            width: 100%;
            min-height: 300px;
            background-color: #f5f5f5;
            display: flex;
            flex-direction: row;
            justify-content: center;
            align-items: center;
        }
        .maskUpload .txt { font-size: 16px; font-weight: bold; color: #666; }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md6">
        <div class="layui-card">
            <div class="layui-card-body">
                <form class="layui-form" action="">
                    <div class="layui-form-item">
                        <div class="layui-form-item">
                            <label class="layui-form-label">图片</label>
                            <div class="layui-input-block">
<#--                                <input type="file" id="file" class="layui-input" onchange="uploadFile();">-->
                                <input type="hidden" id="fileName" />
<#--                                <div style="margin-top: 10px; position: relative;" id="upload_picc"></div>-->

                                <div id="uploader" class="wu-example file-box" style="width: auto;">

                                    <div id="thelist" class="uploader-list flex-center"></div>
                                    <div class="btns">
<#--                                        <div id="picker">点击选择图片文件</div>-->
                                        <div class="maskUpload">
                                            <div class="txt" id="drag_txt">拖到文件到这里</div>
                                            <div style="margin-top: 10px; position: relative;" id="upload_picc"></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <label class="layui-form-label">算法</label>
                            <div class="layui-input-block">
                                <div style="margin-top: 0;">
                                    <#if algorithmList??>
                                        <#list algorithmList as item>
                                            <div class="layui-col-xs4">
                                                <input type="checkbox" name="algorithms" value="${(item.id)!''}" lay-skin="primary" title="${(item.name)!''}" >
                                            </div>
                                        </#list>
                                    </#if>
                                </div>
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <label class="layui-form-label">禁入区域</label>
                            <div class="layui-input-block">
                                <div style="display: flex; flex-direction: row;">
                                    <input type="text" id="marks" name="marks" placeholder="" class="layui-input">
                                    <button type="button" class="pear-btn pear-btn-md pear-btn-primary" style="margin-left: 10px;" onclick="removeMask();">重置</button>
                                </div>
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <label class="layui-form-label">摄像头</label>
                            <div class="layui-input-block">
                                <input type="text" id="cameraId" name="cameraId" placeholder="随便输入数字" class="layui-input">
                            </div>
                        </div>
                        <div class="layui-form-item layui-inline" style="text-align: center; display: flex; justify-content: center; margin-top: 50px;">
                            <button class="pear-btn pear-btn-md pear-btn-primary" lay-submit lay-filter="query" style="margin: 0px 5px;">
                                <i class="layui-icon layui-icon-search"></i>
                                测试
                            </button>
                            <button type="reset" class="pear-btn pear-btn-md" style="margin: 0px 5px;">
                                <i class="layui-icon layui-icon-refresh"></i>
                                重置
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <div class="layui-col-md6">
        <div class="layui-card">
            <div class="layui-card-body">
                <div style="display: flex; flex-direction: row; justify-content: center; position: relative;" id="upload_pic">

                </div>
                <div style="margin-top: 10px;">
                    <div style="font-size: 15px; font-weight: bold; color: #666666; margin-bottom: 10px;">测试结果:</div>
                    <pre id="json_result" style="margin: 0; padding: 0;">
                    </pre>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/webuploader/md5.js"></script>
<script src="/static/js/webuploader/webuploader.min.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup'], function() {
        let $ = layui.jquery;
        let popup = layui.popup;
        let form = layui.form;

        //
        var json_result = '';
        var ctx = null;
        var marks = [];

        //
        form.on('submit(query)', function(data) {
            //
            if($('#fileName').val() == '') {
                popup.failure('请选择需要测试的图片');
                return false;
            }
            //
            var algorithms = [];
            $('input[name="algorithms"]').each(function() {
                if($(this).prop('checked')) {
                    algorithms.push($(this).val());
                }
            });
            //
            if(algorithms.length == 0) {
                popup.failure('请选择需要测试的算法');
                return false;
            }

            //
            var data = {
                'file': $('#fileName').val(),
                'algorithms': JSON.stringify(algorithms),
                'marks': $('#marks').val(),
                'cameraId': $('#cameraId').val(),
                'imgHeight': $('#upload_picc').height()
            };
            $.post('/model/test/predict', data, function(res) {
                if(res.code == 0) {
                    $('#json_result').text(res.data.json);
                    json_result = res.data.json;
                    window.handleXbox(json_result);
                } else {
                    popup.failure(res.msg);
                }
            })
            return false;
        });

        //
        // window.uploadFile = function() {
        //     marks = [];
        //     $('#marks').val('');
        //     //
        //     if($('#file').length == 0 || $('#file')[0].files.length == 0) {
        //         popup.failure('请选择需要测试的图片');
        //         return false;
        //     }
        //     //
        //     var formData = new FormData();
        //     formData.append('file', $('#file')[0].files[0]);
        //     //
        //     $.ajax({
        //         url: "/model/test/upload",
        //         dataType: "json",
        //         async: false,
        //         processData: false,
        //         contentType: false,
        //         data: formData,
        //         method: "POST",
        //         success(res) {
        //             if(res.code == 0) {
        //                 $('#fileName').val(res.data);
        //                 $('#upload_picc').html('');
        //                 $('#upload_picc').append('<img src="/model/test/stream?file=' + res.data + '&_=' + (new Date()).getTime() + '" style="width: 100%; height: 100%;" onload="handleMask(this);" />');
        //                 $('#upload_pic').html('');
        //                 $('#upload_pic').append('<img id="upload_pic_el" src="/model/test/stream?file=' + res.data + '&_=' + (new Date()).getTime() + '" style="width: 100%; height: 100%;"/>');
        //             } else {
        //                 popup.failure(res.msg);
        //             }
        //         },
        //         error(err) {
        //             console.log(err)
        //         }
        //     });
        //     return false;
        // }

        //
        window.handleMask = function(obj) {
            var that = $(obj);
            var pW = that.width();
            var pH = that.height();
            $('#upload_picc_mask').remove();
            $('#upload_picc').append('<canvas width="' + pW +'" height="' + pH + '" class="xcvs" id="upload_picc_mask"></canvas>');
            //
            ctx = document.getElementById('upload_picc_mask').getContext('2d');
            //
            $('#upload_picc_mask').on('mousedown', window.mouseDownListener);
        }

        //
        window.removeMask = function() {
            marks = [];
            $('#marks').val('');
            window.clearCanvas();
        }

        //
        window.clearCanvas = function() {
            ctx.clearRect(0, 0, document.getElementById('upload_picc_mask').width, document.getElementById('upload_picc_mask').height);
        }

        //
        window.handleXbox = function() {
            //
            var that = $('#upload_pic');
            var nW = document.getElementById('upload_pic_el').naturalWidth;
            var dW = that.width();
            var ratio = (dW / nW).toFixed(2);

            //
            if(json_result == '') {
                return false;
            }
            //
            var json = JSON.parse(json_result);
            var len = json.length;
            for(var i = 0; i < len; i++) {
                var it = json[i];
                var type = it['type'];
                var confs = it['confidence'];
                var points = it['position'];
                var sX = parseInt(points[0] * ratio);
                var sY = parseInt(points[1] * ratio);
                var sW = parseInt((points[2] - points[0]) * ratio);
                var sH = parseInt((points[3] - points[1]) * ratio);
                $('#upload_pic').append('<div class="xbox" style="width: ' + sW + 'px; height: ' + sH + 'px; top: ' + sX + 'px; left: ' + sY + 'px;">' + type + ' ' + confs + '</div>');
            }

        }

        //
        window.getClientOffset = function(event) {
            var sLeft = $('#upload_picc').offset().left;
            var sTop = $('#upload_picc').offset().top;
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
            ctx.beginPath();
            ctx.arc(x, y, 4, 0, 2*Math.PI);
            ctx.strokeStyle = "#409EFF";
            ctx.fillStyle = "#409EFF";
            ctx.fill();
            ctx.closePath();
            ctx.stroke();
        }

        //
        window.mouseDownListener = function(event) {
            //
            window.clearCanvas();
            //
            var clickPoint = window.getClientOffset(event);
            marks.push(clickPoint);
            $('#marks').val(JSON.stringify(marks));
            //
            var len = marks.length;
            for(var i = 0; i < len; i++) {
                if(i == 0) {
                    ctx.moveTo(marks[0].x, marks[0].y);
                } else {
                    ctx.lineTo(marks[i].x, marks[i].y);
                }
            }
            //
            ctx.fillStyle = "rgba(64, 158, 255, .4)";
            ctx.fill();
            ctx.lineWidth = 2;
            ctx.strokeStyle = "#409EFF";
            ctx.closePath();
            ctx.stroke();
            //
            for(var i = 0; i < len; i++) {
                window.drawCircle(marks[i].x, marks[i].y);
            }
        }

        //
        var uploader = null;
        window.createUploader = function() {
            if(uploader != null) {
                uploader.reset();
                uploader.destroy();
                uploader = null;
            }

            // create
            uploader = WebUploader.create({
                auto: true,
                swf: '/static/js/webuploader/Uploader.swf',
                server: '/model/test/upload',
                pick: '#picker',
                dnd: ".maskUpload",
                resize: false,
                accept: {
                    title: 'Images',
                    extensions: 'jpg,jpeg,bmp,png',
                    mimeTypes: 'image/*'
                },
            });

            uploader.on('uploadSuccess', function(file, res) {
                if(res.code == 0) {
                    $('#drag_txt').css('display', 'none');
                    $('#fileName').val(res.data);
                    $('#upload_picc').html('');
                    $('#upload_picc').append('<img src="/model/test/stream?file=' + res.data + '&_=' + (new Date()).getTime() + '" style="width: 100%; height: 100%;" onload="handleMask(this);" />');
                    $('#upload_pic').html('');
                    $('#upload_pic').append('<img id="upload_pic_el" src="/model/test/stream?file=' + res.data + '&_=' + (new Date()).getTime() + '" style="width: 100%; height: 100%;"/>');
                } else {
                    $('#drag_txt').css('display', 'block');
                    popup.failure(res.msg);
                }
            });

            uploader.on('uploadError', function( file, a, b, c ) {
                alert('upload error')
            });

            uploader.on('uploadComplete', function( file, a, b, c ) {
                marks = [];
                $('#marks').val('');
                uploader.reset();
            });
        }

        $(document).ready(function() {
            window.createUploader();
        });
    })
</script>
</html>
