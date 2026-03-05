<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>模型管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <link href="/static/js/webuploader/webuploader.css?v=1.0" rel="stylesheet" />
    <style>
        .layui-form-label { width: 110px; }
        .layui-form-label { width: 140px; }
        .layui-input-block { margin-left: 180px; }
    </style>
    <style>
        .tip-info { color: #999999; margin-top: 5px; }
        .layui-form-label { width: 140px; }
        .layui-input-block { margin-left: 180px; }
        .flex-center {
            display: flex; flex-direction: row; justify-content: center;
        }
        .file-box {
            width: 500px; display: flex; flex-direction: row; justify-content: flex-start; align-items: center;
        }
        .progress {
            width: 500px;
            height: 30px;
            background: #f1f1f1;
        }
        .progress .progress-bar {
            background: #36b368;
            height: 30px;
        }
        #thelist > div {
            display: flex; flex-direction: column; justify-content: center;
        }
        .info { font-size: 18px; margin-bottom: 10px; }
        .state { margin-bottom: 5px; color: #36b368; }
        .picker-text { color: #666; font-size: 15px; margin-top: 5px; text-align: center; }
        .input-tip { margin-top: 6px; color: #999999; font-size: 13px; }
    </style>
</head>
<body>
    <form class="layui-form">
        <input type="hidden" name="id" value="${(model.id)!''}">
        <div class="mainBox1">
            <div class="main-container1" style="padding: 15px 15px;">
                <div class="layui-row layui-col-space101">
                    <div class="layui-col-md6 layui-col-xs6">
                        <div class="layui-form-item">
                            <label class="layui-form-label">模型名称</label>
                            <div class="layui-input-block">
                                <input type="text" name="name" id="name" lay-verify="required" autocomplete="off" placeholder="支持中文名称，同一名称为同版本" class="layui-input" value="${(model.name)!''}">
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md6">
                        <div class="layui-form-item">
                            <label class="layui-form-label">模型类型</label>
                            <div class="layui-input-block">
                                <select class="layui-select" name="type" id="type" lay-verify="required">
                                    <option value="1" selected>目标检测</option>
                                    <option value="2">分类任务</option>
                                    <option value="3">分割任务</option>
                                </select>
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md12">
                        <div class="layui-form-item">
                            <label class="layui-form-label">模型描述</label>
                            <div class="layui-input-block">
                                <input type="text" name="description" id="description" autocomplete="off" placeholder="模型描述" class="layui-input" value="${(model.description)!''}">
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md6">
                        <div class="layui-form-item">
                            <label class="layui-form-label">输入参数</label>
                            <div class="layui-input-block">
                                <input type="text" name="inputParam" id="inputParam" lay-verify="required" autocomplete="off" placeholder="input" class="layui-input" value="${(model.inputParam)!''}">
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md6">
                        <div class="layui-form-item">
                            <label class="layui-form-label">输出参数</label>
                            <div class="layui-input-block">
                                <input type="text" name="outputParam" id="outputParam" lay-verify="required" autocomplete="off" placeholder="output" class="layui-input" value="${(model.outputParam)!''}">
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md6">
                        <div class="layui-form-item">
                            <label class="layui-form-label">模型标签</label>
                            <div class="layui-input-block">
                                <input type="text" name="classBiz" id="classBiz" lay-verify="required" autocomplete="off" placeholder="模型使用标签,多个用;分割" class="layui-input" value="${(model.classBiz)!''}">
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md6">
                        <div class="layui-form-item">
                            <label class="layui-form-label">模型保留</label>
                            <div class="layui-input-block">
                                <input type="text" name="classAll" id="classAll" lay-verify="required" autocomplete="off" placeholder="模型包含标签,多个用;分割" class="layui-input" value="${(model.classAll)!''}">
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md6">
                        <div class="layui-form-item">
                            <label class="layui-form-label">图片尺寸</label>
                            <div class="layui-input-block">
                                <div style="display: flex; flex-direction: row; align-items: center;">
                                    <span>宽：</span>
                                    <input type="text" name="imgWidth" id="imgWidth" autocomplete="off" placeholder="宽度" class="layui-input" value="${(model.imgWidth)!'640'}">
                                    <span style="margin-left: 30px;">高：</span>
                                    <input type="text" name="imgHeight" id="imgHeight" autocomplete="off" placeholder="高度" class="layui-input" value="${(model.imgHeight)!'640'}">
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="layui-col-md12">
                        <div class="layui-form-item">
                            <label class="layui-form-label">模型文件</label>
                            <div class="layui-input-block">
                                <input type="hidden" id="onnxName" name="onnxName" value="${(model.onnxName)!''}" />
                                <input type="hidden" id="onnxSize" name="onnxSize" value="${(model.onnxSize)!''}" />
                                <input type="hidden" id="onnxMd5" name="onnxMd5" value="${(model.onnxMd5)!''}" />
                                <input type="hidden" id="originalName" name="originalName" value="${(model.originalName)!''}" />
                                <div id="uploader" class="wu-example file-box" style="width: auto;">
                                    <div id="thelist" class="uploader-list flex-center"></div>
                                    <div class="btns">
                                        <div id="picker">点击选择ONNX文件</div>
                                    </div>
                                </div>
                                <#if (model.onnxName)??>
                                    <div style="font-size: 13px; font-weight: bold;">最后上传文件名称：${(model.onnxName)!''}</div>
                                </#if>
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md12">
                        <div class="layui-form-item">
                            <label class="layui-form-label">模型tag</label>
                            <div class="layui-input-block">
                                <input type="text" name="onnxTag" id="onnxTag" autocomplete="off" placeholder="Tag" class="layui-input" value="${(model.onnxTag)!''}">
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md12">
                        <div class="layui-form-item">
                            <label class="layui-form-label">调用地址</label>
                            <div class="layui-input-block">
                                <input type="text" name="callUrl" id="callUrl" autocomplete="off" placeholder="http调用地址" class="layui-input" value="${(model.callUrl)!''}">
                            </div>
                        </div>
                    </div>
                    <div class="layui-col-md12">
                        <div class="layui-form-item">
                            <label class="layui-form-label">依赖模型</label>
                            <div class="layui-input-block">
                                <#if modelList??>
                                    <#list modelList as item>
                                        <input type="checkbox" name="modelIds[]" title="${(item.name)}" value="${(item.id)!''}" lay-skin="primary" ${(item.checked)!''}>
                                    </#list>
                                </#if>
                            </div>
                        </div>
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
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/webuploader/md5.js"></script>
<script src="/static/js/webuploader/webuploader.min.js"></script>
<script>
    layui.use(['form', 'jquery', 'popup'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;

        //
        var uploader = null, md5File = '';

        //
        form.on('submit(save)', function(data) {
            var loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/model/save', data.field, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    if(res.data.msgType == 20001) {
                        layer.msg(res.data.msgText, {icon:1, time:1000}, function() {
                            parent.layer.close(parent.layer.getFrameIndex(window.name));
                            parent.layui.table.reload('table');
                        });
                    } else {
                        layer.open({
                            title: '模型启用',
                            content: res.data.msgText,
                            icon: 3,
                            closeBtn: 0,
                            btn: ['立即启用', '暂不启用'],
                            yes: function(index, layero){
                                $.post('/model/start', {'modelId': res.data.modelId}, function(sRes) {
                                    if(sRes.code == 0) {
                                        layer.msg(res.data.msgText, {icon:1, time:1000}, function() {
                                            parent.layer.close(parent.layer.getFrameIndex(window.name));
                                            parent.layui.table.reload('table');
                                        });
                                    } else {
                                        popup.failure(sRes.msg);
                                    }
                                });
                            },
                            btn2: function(index, layero){
                                parent.layer.close(parent.layer.getFrameIndex(window.name));
                                parent.layui.table.reload('table');
                            }
                        });
                    }
                } else {
                    popup.failure(res.msg);
                }
            });
            return false;
        });

        $('#close-layer').click(function() {
            parent.layer.close(parent.layer.getFrameIndex(window.name));
        });

        //
        window.fmtFileSize = function(filesize) {
            if(filesize / 1024 < 1024) {
                return (filesize / 1024).toFixed(2) + 'KB';
            }
            if(filesize / (1024 * 1024) < 1024) {
                return (filesize / (1024 * 1024)).toFixed(2) + 'MB';
            }
            return (filesize / (1024 * 1024 * 1024)).toFixed(2) + 'GB';
        }

        //监听分块上传过程中的时间点
        WebUploader.Uploader.register({
            "before-send-file": "beforeSendFile",  // 整个文件上传前
            "before-send": "beforeSend",  // 每个分片上传前
            "after-send-file": "afterSendFile"  // 分片上传完毕
        },{
            //时间点1：所有分块进行上传之前调用此函数 ，检查文件存不存在
            beforeSendFile:function(file) {
                var deferred = WebUploader.Deferred();

                //
                uploader.md5File(file).then(function(md5) {
                    console.log('md5 result:', md5);
                    md5File = md5;

                    $.ajax({
                        type: "POST",
                        url: "/model/checkFile",
                        data: {
                            md5File: md5,
                            fileName: file.name
                        },
                        async: false,  // 同步
                        dataType: "json",
                        success:function(response) {
                            if(response.code == 0) {
                                if(response.data.errType == 200) {
                                    deferred.resolve();
                                } else if(response.data.errType == 4001) {
                                    popup.failure(response.data.errMsg);
                                    $('#thelist').html('');
                                    $('#picker').css('display', 'block');
                                    window.createUploader();
                                } else if(response.data.errType == 4002) {
                                    layer.open({
                                        title: '文件名处理',
                                        content: response.data.errMsg,
                                        icon: 3,
                                        closeBtn: 0,
                                        btn: ['覆盖原文件', '原文件改名', '取消'],
                                        yes: function(index, layero){
                                            deferred.resolve();
                                        },
                                        btn2: function(index, layero){
                                            $.post('/model/rename', {'fileName': file.name}, function(r) {
                                               if(r.code == 0) {
                                                   popup.success('原文件名称修改成功');
                                                   deferred.resolve();
                                               } else {
                                                   popup.failure(r.msg);
                                                   $('#thelist').html('');
                                                   $('#picker').css('display', 'block');
                                                   window.createUploader();
                                               }
                                            });
                                        },
                                        btn3: function(index, layero){
                                            $('#thelist').html('');
                                            $('#picker').css('display', 'block');
                                            window.createUploader();
                                        }
                                    });
                                }
                            }
                        }
                    }, function (jqXHR, textStatus, errorThrown) { //任何形式的验证失败，都触发重新上传
                        deferred.resolve();
                    });
                });
                return deferred.promise();
            },
            //时间点2：如果有分块上传，则每个分块上传之前调用此函数  ，判断分块存不存在
            beforeSend:function(block){
                var deferred = WebUploader.Deferred();
                $.ajax({
                    type: "POST",
                    url: "/model/checkChunk",
                    data:{
                        md5File: md5File,  //文件唯一标记
                        chunk: block.chunk,  //当前分块下标
                    },
                    dataType: "json",
                    success:function(response){
                        if(response){
                            deferred.reject(); //分片存在，跳过
                        }else{
                            deferred.resolve();  //分块不存在或不完整，重新发送该分块内容
                        }
                    }
                }, function (jqXHR, textStatus, errorThrown) { //任何形式的验证失败，都触发重新上传
                    deferred.resolve();
                });
                return deferred.promise();
            },
            //时间点3：分片上传完成后，通知后台合成分片
            afterSendFile: function (file) {
                var chunksTotal = Math.ceil(file.size / (5*1024*1024));
                if (chunksTotal >= 1) {
                    //合并请求
                    var deferred = WebUploader.Deferred();
                    $.ajax({
                        type: "POST",
                        url: "/model/merge",
                        data: {
                            name: file.name,
                            md5File: md5File,
                            chunks: chunksTotal
                        },
                        cache: false,
                        async: false,  // 同步
                        dataType: "json",
                        success:function(response) {
                            if(response.code == 0) {
                                $('#' + file.id).find('p.state').text('模型文件上传完成');
                                $('#' + file.id).find('.progress').fadeOut();

                                $('#onnxName').val(response.data.onnxName);
                                $('#onnxMd5').val(response.data.onnxMd5);
                                $('#onnxSize').val(response.data.onnxSize);
                                popup.success('文件上传完成');
                            }else{
                                $('#' + file.id).find('p.state').text('模型文件错误，请重置后重试');
                                deferred.reject();
                            }
                        }
                    })
                    return deferred.promise();
                }
            }
        });

        //
        window.createUploader = function() {
            if(uploader != null) {
                uploader.reset();
                uploader.destroy();
                uploader = null;
            }

            // create
            uploader = WebUploader.create({
                auto: true,// 选完文件后，是否自动上传。
                swf: '/static/js/webuploader/Uploader.swf',
                server: '/model/chunkUpload',
                pick: '#picker',// 选择文件的按钮。可选。
                chunked: true,
                chunkSize: 5*1024*1024,//5M
                chunkRetry: 3
            });

            //上传添加参数
            uploader.on('uploadBeforeSend', function (obj, data, headers) {
                data.md5File = md5File;
            });

            // 当有文件被添加进队列的时候
            uploader.on('fileQueued', function( file ) {
                $("#picker").hide();//隐藏上传框
                $("#thelist").append( '<div id="' + file.id + '" class="item">' +
                    '<h4 class="info">' + file.name + '  ' + window.fmtFileSize(file.size) + '</h4>' +
                    '<p class="state"></p>' +
                    '</div>' );
            });

            // 文件上传过程中创建进度条实时显示。
            uploader.on('uploadProgress', function(file, percentage) {
                console.log(percentage)
                var $li = $('#'+file.id ),
                    $percent = $li.find('.progress .progress-bar');

                // 避免重复创建
                if (!$percent.length ) {
                    $percent = $('<div class="progress progress-striped active">' +
                        '<div class="progress-bar" role="progressbar" style="width: 0%"></div>' +
                        '</div>').appendTo( $li ).find('.progress-bar');
                }
                $li.find('p.state').text('正在上传中' + (percentage * 100).toFixed(2) + '%');
                $percent.css( 'width', percentage * 100 + '%' );
            });
        }

        $(document).ready(function() {
            window.createUploader();
        })
    })
</script>
</html>
