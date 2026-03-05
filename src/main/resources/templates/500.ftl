<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>500系统异常</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <link rel="stylesheet" href="/static/admin/css/other/result.css" />
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10" style="padding: 12px;">
    <div class="layui-card">
        <div class="layui-card-body">
            <div class="result">
                <div class="error">
                    <svg viewBox="64 64 896 896" data-icon="close-circle" width="80px" height="80px" fill="currentColor" aria-hidden="true" focusable="false" class=""><path d="M685.4 354.8c0-4.4-3.6-8-8-8l-66 .3L512 465.6l-99.3-118.4-66.1-.3c-4.4 0-8 3.5-8 8 0 1.9.7 3.7 1.9 5.2l130.1 155L340.5 670a8.32 8.32 0 0 0-1.9 5.2c0 4.4 3.6 8 8 8l66.1-.3L512 564.4l99.3 118.4 66 .3c4.4 0 8-3.5 8-8 0-1.9-.7-3.7-1.9-5.2L553.5 515l130.1-155c1.2-1.4 1.8-3.3 1.8-5.2z"></path><path d="M512 65C264.6 65 64 265.6 64 513s200.6 448 448 448 448-200.6 448-448S759.4 65 512 65zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z"></path></svg>
                </div>
                <h2 class="title">错误提示</h2>
                <p class="desc" style="padding-bottom: 80px;">
                    ${(error)!''}
                </p>
<#--                <div class="content">-->
<#--                    ${(error)!''}-->
<#--                </div>-->
            </div>
        </div>
    </div>
</div>
</body>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['form', 'jquery', 'popup', 'laytpl'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let laytpl = layui.laytpl;

        //
        $('#close-layer').click(function() {
            parent.layer.close(parent.layer.getFrameIndex(window.name));
        });
    })
</script>
</html>
