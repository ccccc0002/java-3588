<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>账号管理</title>
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

<script type="text/html" id="table-toolbar">
    <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="add" id="btnAdd">
        <i class="layui-icon layui-icon-add-1"></i>
        新增账号
    </button>
</script>

<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
    <a href="#" style="color: #DD4A68; margin-left: 15px;" lay-event="remove">删除</a>
</script>

<script type="text/html" id="stateTpl">
    {{# if(d.state == 0){ }}
    <span style="color:#16b777;">启用</span>
    {{# } else { }}
    <span style="color:#ff5722;">禁用</span>
    {{# } }}
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'jquery', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let permissions = {can_manage_accounts: false};

        function loadPermissions(next) {
            $.post('/account/permissions', {}, function(res) {
                if (res && res.code === 0 && res.data) {
                    permissions = res.data;
                }
                if (typeof next === 'function') {
                    next();
                }
            });
        }

        function renderTable() {
            table.render({
                elem: '#table',
                id: 'table',
                url: '/account/listData',
                method: 'post',
                page: false,
                cols: [[
                    {title: '姓名', field: 'name', templet: function(d) {
                        var name = (d.name || '').trim();
                        if (!name || /^\?+$/.test(name)) {
                            return '管理员';
                        }
                        return name;
                    }},
                    {title: '账号', field: 'account'},
                    {title: '角色', field: 'roleLabel', width: 130},
                    {title: '状态', field: 'state', templet: '#stateTpl', width: 110},
                    {title: '创建时间', field: 'createdAt', templet: '<span>{{layui.util.toDateString(d.createdAt, "yyyy-MM-dd HH:mm:ss")}}</span>', width: 180},
                    {title: '操作', toolbar: '#table-actions', align: 'left', width: 140, fixed: 'right'}
                ]],
                skin: 'line',
                height: 'full-148',
                toolbar: '#table-toolbar',
                defaultToolbar: [{
                    title: '刷新',
                    layEvent: 'refresh',
                    icon: 'layui-icon-refresh'
                }],
                done: function() {
                    if (!permissions.can_manage_accounts) {
                        $('#btnAdd').prop('disabled', true).addClass('layui-btn-disabled');
                    }
                }
            });
        }

        table.on('tool(table)', function(obj) {
            if (!permissions.can_manage_accounts) {
                popup.failure('无权限操作');
                return;
            }
            if (obj.event === 'remove') {
                window.removeData(obj);
            } else if (obj.event === 'edit') {
                window.editForm(obj);
            }
        });

        table.on('toolbar(table)', function(obj) {
            if (obj.event === 'add') {
                if (!permissions.can_manage_accounts) {
                    popup.failure('无权限操作');
                    return;
                }
                window.addForm();
            } else if (obj.event === 'refresh') {
                window.refreshTable();
            }
        });

        window.addForm = function() {
            layer.open({
                type: 2,
                title: '新增账号',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/account/form'
            });
        };

        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: '编辑账号',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/account/form?id=' + obj.data.id
            });
        };

        window.refreshTable = function() {
            table.reload('table');
        };

        window.removeData = function(obj) {
            layer.confirm('确定删除该账号吗？', {icon: 3, title: '提示'}, function(index) {
                layer.close(index);
                let loading = layer.load(2);
                $.post('/account/delete', {'id': obj.data.id}, function(res) {
                    layer.close(loading);
                    if (res.code === 0) {
                        layer.msg('操作成功', {icon: 1, time: 900}, function() {
                            window.refreshTable();
                        });
                    } else {
                        popup.failure(res.msg);
                    }
                });
            });
        };

        loadPermissions(renderTable);
    });
</script>
</body>
</html>
