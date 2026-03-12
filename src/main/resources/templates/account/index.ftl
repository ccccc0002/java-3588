<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>Account Management</title>
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
        Add Account
    </button>
</script>

<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">Edit</a>
    <a href="#" style="color: #DD4A68; margin-left: 15px;" lay-event="remove">Delete</a>
</script>

<script type="text/html" id="stateTpl">
    {{# if(d.state == 0){ }}
    <span style="color:#16b777;">Enabled</span>
    {{# } else { }}
    <span style="color:#ff5722;">Disabled</span>
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
                    {title: 'Name', field: 'name'},
                    {title: 'Account', field: 'account'},
                    {title: 'Role', field: 'roleLabel', width: 130},
                    {title: 'State', field: 'state', templet: '#stateTpl', width: 110},
                    {title: 'Created', field: 'createdAt', templet: '<span>{{layui.util.toDateString(d.createdAt, "yyyy-MM-dd HH:mm:ss")}}</span>', width: 180},
                    {title: 'Actions', toolbar: '#table-actions', align: 'left', width: 140, fixed: 'right'}
                ]],
                skin: 'line',
                height: 'full-148',
                toolbar: '#table-toolbar',
                defaultToolbar: [{
                    title: 'Refresh',
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
                popup.failure('permission denied');
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
                    popup.failure('permission denied');
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
                title: 'Add Account',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/account/form'
            });
        };

        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: 'Edit Account',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/account/form?id=' + obj.data.id
            });
        };

        window.refreshTable = function() {
            table.reload('table');
        };

        window.removeData = function(obj) {
            layer.confirm('Delete selected account?', {icon: 3, title: 'Warning'}, function(index) {
                layer.close(index);
                let loading = layer.load(2);
                $.post('/account/delete', {'id': obj.data.id}, function(res) {
                    layer.close(loading);
                    if (res.code === 0) {
                        layer.msg('Success', {icon: 1, time: 900}, function() {
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

