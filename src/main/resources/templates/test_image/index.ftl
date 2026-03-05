<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>手动抓图测试</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .layui-form-label { width: 100px; }
        .layui-input-block { margin-left: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        #canvasBox { position: absolute; left: 5px; right: 5px; top: 5px; bottom: 5px; }
        #logs li {
            margin-bottom: 7px;
        }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <div class="layui-row layui-col-space10">
                    <div class="layui-col-md8">
<#--                        <div href="#" style="float: right; width: 50px; height: 15px; border: 0px solid red;" ondblclick="window.doDiscard();"></div>-->
                        <form class="layui-form" action="javascript:void(0);" style="margin: 0 auto; padding-top: 40px;">
                            <div class="layui-form-item">
                                <label class="layui-form-label">摄像头编码:</label>
                                <div class="layui-input-block">
                                    <div><input type="text" id="indexCode" class="layui-input" value="6d0483b0d6ff4d4dba5e720e372db370"/> </div>
                                    安徽/苏湾/东大门
                                </div>
                            </div>

                            <div class="layui-form-item">
                                <label class="layui-form-label">时间间隔(秒):</label>
                                <div class="layui-input-block">
                                    <div><input type="text" id="timeInterval" class="layui-input" value="10"/> </div>
                                </div>
                            </div>

                            <button type="submit" class="pear-btn pear-btn-primary pear-btn-sm" lay-submit="" lay-filter="save">
                                <i class="layui-icon layui-icon-ok"></i>
                                开始
                            </button>
                            <button class="pear-btn pear-btn-sm" id="closex">
                                <i class="layui-icon layui-icon-close"></i>
                                停止
                            </button>
                        </form>

                        <div id="">status<span id="status">停止。。</span><span id="timecount">0次</span></div>

                        <div>日志</div>
                        <lu id="logs">
                            <li></li>
                        </lu>

                    </div>

                    <div class="layui-col-md4">
                        <img src="" id="camera_img" />
                    </div>

                </div>
            </div>
        </div>
    </div>
</div>
</body>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['jquery', 'form'], function() {
        let $ = layui.jquery;
        let form = layui.form;

        var timer_interval = null;
        var timer_timeout = null;
        var time_count = 0;

        form.on('submit(save)', function(data) {
            var indexCode = $('#indexCode').val();
            var timeInterval = $('#timeInterval').val();

            if($.trim(indexCode) == '') {
                popup.failure('请输入摄像头编码');
                return ;
            }

            if($.trim(timeInterval) == '') {
                popup.failure('请输入抓图时间间隔');
                return ;
            }

            $('#logs li').remove();

            clearTimeout(timer_timeout);
            clearInterval(timer_interval);
            time_count = 0;

            $('#status').text('抓图中。。');
            window.getImage();

            timer_interval = setInterval(function() {
                $('#status').text('抓图中。。');
                window.getImage();
            }, timeInterval * 1000);

            //
            timer_timeout = setTimeout(function() {
                $('#status').text('停止。。');
                timer_timeout = null;
                clearInterval(timer_interval);
            }, 3 * 60 * 1000);

            return false;
        });

        window.getImage = function () {
            var indexCode = $('#indexCode').val();
            $.post('/testimage/get', {'indexCode': indexCode}, function(res) {
                $('#logs').append('<li>' + res.data.params + '  |  ' + res.data.result + '  |  ' + res.data.time + '</li>');
                $('#camera_img').attr('src', res.data.picUrl);
                time_count++;
                $('#timecount').text(time_count + '次');
            });
        }

        $('#closex').on('click', function() {
            clearTimeout(timer_timeout);
            clearInterval(timer_interval);
            $('#status').text('停止。。');
        })

    })
</script>
</html>
