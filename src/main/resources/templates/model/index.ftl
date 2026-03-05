<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>模型管理</title>
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
    <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="add">
        <i class="layui-icon layui-icon-add-1"></i>
        新增模型
    </button>
</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
    <a href="#" style="color: #999999; margin-left: 10px;" lay-event="ver">版本</a>
    <a href="#" style="color: #999999; margin-left: 10px;" lay-event="test">测试</a>
</script>
<script type="text/html" id="versionTpl">
    <a href="javascript:void(0);" style="color: #409EFF;" onclick="handleVersion('{{ d.id }}');">{{ d.versionCount }}</a>
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
            title: '版本数量',
            field: 'versionCount',
            align: 'center'
        }, {
            title: '操作',
            toolbar: '#table-actions',
            align: 'left',
            width: 160,
            fixed: 'right'
        }];

        //
        table.render({
            elem: '#table',
            url: '/model/listData',
            method: 'post',
            page: true,
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
            } else if(obj.event == 'edit') {
                window.editForm(obj);
            } else if(obj.event == 'ver') {
                window.handleVersion(obj.data['id']);
            } else if(obj.event == 'test') {
                window.testForm();
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
        window.addForm = function() {
            layer.open({
                type: 2,
                title: '新增模型',
                shade: 0.1,
                area: ['90%', '90%'],
                content: '/model/form'
            });
        }

        //
        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: '编辑模型',
                shade: 0.1,
                area: ['90%', '90%'],
                content: '/model/form?id=' + obj.data.id
            });
        }

        //
        window.testForm = function() {
            layer.open({
                type: 2,
                title: '模型测试',
                shade: 0.1,
                area: ['90%', '90%'],
                content: '/model/test'
            });
        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }

        //
        window.removeData = function (obj) {
            layer.confirm('确定删除该算法吗?', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                var loading = layer.load(2);
                $.post('/model/delete', {'id': obj.data['id']}, function(res) {
                    layer.close(loading);
                    if(res.code == 0) {
                        layer.msg('操作成功', {icon:1, time:1000}, function() {
                            window.refreshTable();
                        });
                    } else {
                        popup.failure(res.msg);
                    }
                });
            });
        }

        //
        window.handleVersion = function(modelId) {
            layer.open({
                type: 2,
                title: '模型版本管理',
                shade: 0.1,
                area: ['90%', '90%'],
                content: '/model/version?modelId=' + modelId
            });
        }
    })
</script>
</html>
