<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>算法管理</title>
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
        新增算法
    </button>
</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
    <a href="#" style="color: #DD4A68; margin-left: 15px;" lay-event="remove">删除</a>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let util = layui.util;

        let filecols = [{
            title: '文件名称',
            field: 'file_name'
        }, {
            title: '文件大小',
            field: 'file_size'
        }];

        //
        let cols = [{
            title: '算法名称',
            field: 'name'
        },{
            title: '算法英文',
            field: 'nameEn'
        }, {
            title: '更新时间',
            field: 'updatedAt',
            templet:function (d) {
                var t1=d.updatedAt
                return util.toDateString(t1);
            }
        },  {
            title: '操作',
            toolbar: '#table-actions',
            align: 'left',
            width: 120,
            fixed: 'right'
        }];


        table.render({
            elem: '#table',
            url: '/algorithm/listData',
            method: 'post',
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
            } else if(obj.event == 'edit') {
                window.editForm(obj);
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
                title: '新增算法',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/algorithm/form'
            });
        }

        //
        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: '编辑算法',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/algorithm/form?id=' + obj.data.id
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
                $.post('/algorithm/delete', {'id': obj.data['id']}, function(res) {
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
    })
</script>
</html>
