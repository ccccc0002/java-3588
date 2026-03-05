<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>账号管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <script type="text/html" id="centreTpl">
        <option value="" selected>-Select-</option>
        {{# for(var i = 0, len = d.length; i < len; i++){ }}
        <option value="{{ d[i].id }}">{{ d[i].title }}</option>
        {{# } }}
    </script>
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
        新增账号
    </button>
</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
    <a href="#" style="color: #DD4A68; margin-left: 15px;" lay-event="remove">删除</a>
</script>
<script type="text/html" id="stateTpl">
    {{#  if(d.state=='0'){ }}
    正常
    {{#  } else { }}
    禁用
    {{# } }}
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let form = layui.form;

        //
        form.render('select');

        //
        form.on('submit(query)', function (data) {
            table.reload('table', {
                where: data.field
            })
            return false;
        });

        //
        let cols = [{
            title: '员工姓名',
            field: 'name'
        }, {
            title: '登录账号',
            field: 'account'
        }, {
            title: '账号状态',
            field: 'state',
            templet: '#stateTpl'
        }, {
            title: '创建日期',
            field: 'createdAt',
            templet: '<span>{{layui.util.toDateString(d.createdAt, "yyyy-MM-dd")}}</span>'
        }, {
            title: '操作',
            toolbar: '#table-actions',
            align: 'left',
            width: 120,
            fixed: 'right'
        }];

        //
        table.render({
            elem: '#table',
            url: '/account/listData',
            method: 'post',
            page: false,
            cols: [cols],
            skin: 'line',
            height: 'full-148',
            toolbar: '#table-toolbar',
            defaultToolbar: [{
                title: 'Refresh',
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
                title: '新增账号',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/account/form'
            });
        }

        //
        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: '编辑账号',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/account/form?id=' + obj.data.id
            });
        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }

        //
        window.removeData = function (obj) {
            layer.confirm('确定删除该账号吗?', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                var loading = layer.load(2);
                $.post('/account/delete', {'id': obj.data['id']}, function(res) {
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
