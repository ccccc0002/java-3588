<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>模型版本管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <table id="table" lay-filter="table"></table>
            </div>
        </div>
    </div>
</div>
</body>
<script type="text/html" id="table-toolbar">
    <h1 style="font-size: 16px; font-weight: bold;">模型版本历史</h1>
</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #009688;" lay-event="start">启用</a><span style="padding: 0px 5px; color: #ddd;">|</span>
    <a href="#" style="color: #DD4A68;" lay-event="remove">删除</a>
</script>
<script type="text/html" id="stateTpl">
    {{# if(d.state == 0) { }}
    <span class="layui-badge layui-bg-green">启用</span>
    {{# } else { }}
    <span class="layui-badge">停用</span>
    {{# } }}
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;

        //
        let cols = [{
            title: '模型名称',
            field: 'name'
        }, {
            title: '模型类型',
            field: 'typeName'
        }, {
            title: '文件名称',
            field: 'onnxName'
        }, {
            title: '文件Tag',
            field: 'onnxTag'
        }, {
            title: '文件大小',
            field: 'fileSize'
        }, {
            title: '文件md5',
            field: 'onnxMd5'
        }, {
            title: '创建时间',
            field: 'createdAt',
            align: 'center',
            templet: '<span>{{ layui.util.toDateString(d.createdAt, "yyyy-MM-dd HH:mm:ss") }}</span>'
        }, {
            title: '状态',
            field: 'state',
            align: 'center',
            templet: '#stateTpl',
            width: 80,
        }, {
            title: '操作',
            toolbar: '#table-actions',
            align: 'center',
            width: 110,
            fixed: 'right'
        }];

        //
        table.render({
            elem: '#table',
            url: '/model/listVersion',
            method: 'post',
            where: {
                'modelId': '${modelId!'0'}'
            },
            page: false,
            cols: [cols],
            skin: 'line',
            height: 'full-148',
            toolbar: '#table-toolbar',
            defaultToolbar: [{
                title: '刷新',
                layEvent: 'refresh',
                icon: 'layui-icon-refresh'
            }]
        });

        //
        table.on('tool(table)', function(obj) {
            if(obj.event == 'remove') {
                window.removeData(obj);
            } else if(obj.event == 'start') {
                window.startModel(obj);
            }
        });

        //
        table.on('toolbar(table)', function(obj) {
            if(obj.event == 'add') {
                window.addForm();
            } else if(obj.event == 'refresh') {
                window.refreshTable();
            }
        });

        //
        window.startModel = function(obj) {
            var loading = layer.load(2);
            $.post('/model/start', {'modelId': obj.data['id']}, function(res) {
                layer.close(loading);
                if(res.code == 0) {
                    layer.msg('操作成功', {icon:1, time:1000}, function() {
                        parent.layui.table.reload('table');
                        window.refreshTable();
                    });
                } else {
                    popup.failure(res.msg);
                }
            });
        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }

        //
        window.removeData = function (obj) {
            layer.confirm('确定删除该模型吗?', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                var loading = layer.load(2);
                $.post('/model/delete', {'id': obj.data['id']}, function(res) {
                    layer.close(loading);
                    if(res.code == 0) {
                        layer.msg('操作成功', {icon:1, time:1000}, function() {
                            parent.layui.table.reload('table');
                            window.refreshTable();
                        });
                    } else {
                        popup.failure(res.msg);
                    }
                });
            });
        }
    })
</script>
</html>
