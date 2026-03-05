<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>算法管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .layui-form-label { width: 110px; }
        .layui-form-label { width: 140px; }
        .layui-input-block { margin-left: 180px; }
        .layui-progress-text {top: 0px !important; }
    </style>
</head>
<body>
    <form class="layui-form">
        <input type="hidden" name="id" value="${(algorithm.id)!''}">
        <div class="mainBox">
            <div class="main-container">
                <div class="layui-form-item">
                    <label class="layui-form-label">算法名称</label>
                    <div class="layui-input-block">
                        <input type="text" name="name" lay-verify="required" autocomplete="off" placeholder="请输入算法名称" class="layui-input" value="${(algorithm.name)!''}">
                    </div>
                </div>


                <div class="layui-form-item">
                    <label class="layui-form-label">算法英文</label>
                    <div class="layui-input-block">
                        <input type="text" id="suanfaEn" name="nameEn" lay-verify="required" autocomplete="off" placeholder="请输入算法英文" class="layui-input" value="${(algorithm.nameEn)!''}">
                    </div>
                </div>

                <button type="button" class="pear-btn pear-btn-primary pear-btn-sm" lay-submit="" lay-filter="search">

                    搜索
                </button>
                <button type="button" id="download_button" class="pear-btn pear-btn-primary pear-btn-sm" lay-submit="" lay-filter="download">
                    保存到本地
                </button>
                <div class="layui-form-item">
                    远程FTP服务器文件
                    <table id="fileTable" lay-filter="fileTable"></table>

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
<script type="text/html" id="table-toolbar2">

</script>
<script>
    layui.use(['form', 'jquery', 'popup','element'], function() {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let table = layui.table;
        let element = layui.element;
        let isFishih = false;

        let filecols = [{
            title: '文件名称',
            field: 'fileName'
        }, {
            title: 'MD5核对结果',
            field: 'md5Str'
        }, {
            title: '文件大小',
            field: 'fileSize'
        }, {
            title: '进度',
            field: 'process',
            width: 100,
            templet:function(d){
                var html = '<div class="layui-progress" lay-showPercent="yes">';
                html += '<div class="layui-progress-bar" lay-percent="'+ d.process + '%"></div>';
                html += '</div>';
                return html;
            }
        }];
        let defaultSearch = $("#suanfaEn").val();
        if(form.val(`fileTable`).nameEn){
            defaultSearch = form.val(`fileTable`).nameEn;
        }
        table.render({
            elem: '#fileTable',
            url: '/algorithm/search?suanfa='+defaultSearch,
            page: false,
            cols: [filecols],
            skin: 'line',
            height: 'full-148',
            done: function (res,currentcount) {
                element.render();
            }


        });

        //
        form.on('submit(save)', function(data) {
            var loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/algorithm/save', data.field, function(res) {
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

        form.on('submit(search)', function(data) {

            if(data.field.nameEn){
                defaultSearch = data.field.nameEn;

                table.reloadData('fileTable',{
                    url: '/algorithm/search?suanfa='+defaultSearch ,

                });
            }
            //var loading = layer.load(2, { shade: [0.15,'#000'] });

            // $.get('/algorithm/search?suanfa='+data.field.nameEn, function(res) {
            //     layer.close(loading);
            // });
            return false;
        });

        form.on('submit(download)', function(data) {
            var loading = layer.load(2, { shade: [0.15,'#000'] });
            $.get('/algorithm/search?suanfa='+data.field.nameEn, function(res) {
                //确保有数据才能请求下载
                if(res.code != 0){
                        layer.close(loading);
                    popup.failure(res.msg);
                    $('#download_button').addClass("layui-btn-disabled").attr("disabled",true);
                    return false;
                }

            });
            $('#download_button').addClass("layui-btn-disabled").attr("disabled", true);
            popup.success('下载中,请不要关闭页面,等待所有文件下载完毕');

            $.ajax({
                    'url': '/algorithm/download?suanfa=' + data.field.nameEn,
                    'method': 'get',
                    'success': function (res) {

                        if (res.code != 0) {
                            layer.close(loading);
                            popup.failure(res.msg);
                            $('#download_button').addClass("layui-btn-disabled").attr("disabled", true);
                            isFishih = true;
                        }else{
                            popup.success("下载完毕");
                            if(data.field.nameEn){
                                defaultSearch = data.field.nameEn;
                                table.reloadData('fileTable',{
                                    url: '/algorithm/search?suanfa='+defaultSearch ,
                                });
                            }
                            isFishih = true;
                            $('#download_button').addClass("layui-btn-disabled").attr("disabled", true);
                            layer.close(loading);
                        }
                    }
                }
            );
            //1-10秒一次请求接口刷新
            // var intervalId = setInterval(function (){
            //     if(isFishih) {
            //         clearInterval(intervalId);
            //         return false
            //     }
            //     if(data.field.nameEn){
            //         defaultSearch = data.field.nameEn;
            //         table.reloadData('fileTable',{
            //             url: '/algorithm/search?suanfa='+defaultSearch ,
            //         });
            //     }
            // },2000);
            //文件下载完毕就停止轮循

            return false;
        });
        form.on('submit(download2)', function(data) {
            if(data.field.nameEn){
                defaultSearch = data.field.nameEn;

                table.reloadData('fileTable',{
                    url: '/algorithm/search?suanfa='+defaultSearch ,

                });
            }

            return false;
        });
        $('#close-layer').click(function() {
            parent.layer.close(parent.layer.getFrameIndex(window.name));
        });
    })
</script>
</html>
