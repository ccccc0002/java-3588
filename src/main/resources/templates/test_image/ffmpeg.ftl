<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>ffmpeg测试</title>
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
                                <label class="layui-form-label">encoder:</label>
                                <div class="layui-input-block">
                                    <div><input type="text" id="encoder" class="layui-input" value="h264_videotoolbox"/> </div>
                                </div>
                            </div>

                            <div class="layui-form-item">
                                <label class="layui-form-label">decoder:</label>
                                <div class="layui-input-block">
                                    <div><input type="text" id="decoder" class="layui-input" value="h264_videotoolbox"/> </div>
                                </div>
                            </div>

                            <button type="submit" class="pear-btn pear-btn-primary pear-btn-sm" lay-submit="" lay-filter="save">
                                <i class="layui-icon layui-icon-ok"></i>
                                测试
                            </button>
                        </form>
                    </div>
                </div>

                <div>
                    <div id="encoder_show" style="padding: 5px; border: 1px solid red;"></div>
                    <div id="decoder_show" style="padding: 5px; border: 1px solid blue;"></div>
                    <div id="encoders_show" style="word-break:break-all;width:800px; overflow:auto; padding: 5px; border: 1px solid yellow;"></div>
                    <div id="decoders_show" style="word-break:break-all;width:800px; overflow:auto; padding: 5px; border: 1px solid orangered;"></div>
                    <div id=""></div>
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
            var encoder = $('#encoder').val();
            var decoder = $('#decoder').val();

            $.post('/testimage/ffmpeg', {'encoder': encoder, 'decoder': decoder}, function(res) {
                $('#encoder_show').html('');
                $('#decoder_show').html('');
                $('#encoders_show').html('');
                $('#decoders_show').html('');

                if(res.code == 0) {
                    $('#encoder_show').html(res.data.encoderc);
                    $('#decoder_show').html(res.data.decoderc);
                    $('#encoders_show').html(res.data.encodecs);
                    $('#decoders_show').html(res.data.decodecs);
                }
            });

            return false;
        });

    })
</script>
</html>
