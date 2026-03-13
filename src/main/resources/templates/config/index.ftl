<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>System Config</title>
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
    <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="add">
        <i class="layui-icon layui-icon-add-1"></i>
        Add Config
    </button>
    <button class="pear-btn pear-btn-warming pear-btn-md" lay-event="license" style="margin-left: 8px;">
        License
    </button>
    <button class="pear-btn pear-btn-normal pear-btn-md" lay-event="network" style="margin-left: 8px;">
        Network
    </button>
    <button class="pear-btn pear-btn-danger pear-btn-md" lay-event="scheduler" style="margin-left: 8px;">
        Scheduler
    </button>
    <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="branding" style="margin-left: 8px;">
        Branding
    </button>
</script>

<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">Edit</a>
    <a href="#" style="color: #DD4A68; margin-left: 15px;" lay-event="remove">Delete</a>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'jquery', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let permissions = {can_write_system: false};

        let cols = [[
            { title: 'Name', field: 'name' },
            { title: 'Tag', field: 'tag' },
            { title: 'Value', field: 'val' },
            { title: 'Actions', toolbar: '#table-actions', align: 'left', width: 130, fixed: 'right' }
        ]];

        table.render({
            elem: '#table',
            url: '/config/listData',
            method: 'post',
            page: false,
            cols: cols,
            skin: 'line',
            height: 'full-148',
            toolbar: '#table-toolbar',
            defaultToolbar: [{
                title: 'Refresh',
                layEvent: 'refresh',
                icon: 'layui-icon-refresh'
            }]
        });

        table.on('tool(table)', function(obj) {
            if (!permissions.can_write_system) {
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
                if (!permissions.can_write_system) {
                    popup.failure('permission denied');
                    return;
                }
                window.addForm();
            } else if (obj.event === 'license') {
                if (!permissions.can_write_system) {
                    popup.failure('permission denied');
                    return;
                }
                window.openLicense();
            } else if (obj.event === 'network') {
                if (!permissions.can_write_system) {
                    popup.failure('permission denied');
                    return;
                }
                window.openNetwork();
            } else if (obj.event === 'scheduler') {
                if (!permissions.can_write_system) {
                    popup.failure('permission denied');
                    return;
                }
                window.openScheduler();
            } else if (obj.event === 'branding') {
                if (!permissions.can_write_system) {
                    popup.failure('permission denied');
                    return;
                }
                window.openBranding();
            } else if (obj.event === 'refresh') {
                window.refreshTable();
            }
        });

        window.addForm = function() {
            layer.open({
                type: 2,
                title: 'Add Config',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/config/form'
            });
        };

        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: 'Edit Config',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/config/form?id=' + obj.data.id
            });
        };

        window.openLicense = function() {
            layer.open({
                type: 2,
                title: 'License',
                shade: 0.1,
                area: ['70%', '70%'],
                content: '/config/license'
            });
        };

        window.openNetwork = function() {
            layer.open({
                type: 2,
                title: 'Network',
                shade: 0.1,
                area: ['80%', '80%'],
                content: '/config/network'
            });
        };

        window.openScheduler = function() {
            layer.open({
                type: 2,
                title: 'Scheduler',
                shade: 0.1,
                area: ['70%', '78%'],
                content: '/config/scheduler'
            });
        };

        window.openBranding = function() {
            layer.open({
                type: 2,
                title: 'Branding',
                shade: 0.1,
                area: ['72%', '82%'],
                content: '/config/branding'
            });
        };

        window.refreshTable = function() {
            table.reload('table');
        };

        window.removeData = function(obj) {
            layer.confirm('Delete selected config?', {icon: 3, title: 'Warning'}, function(index) {
                layer.close(index);
                let loading = layer.load(2);
                $.post('/config/delete', {'id': obj.data.id}, function(res) {
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

        function applyPermissionUi() {
            if (permissions.can_write_system) {
                return;
            }
            $('button[lay-event=add],button[lay-event=license],button[lay-event=network],button[lay-event=scheduler],button[lay-event=branding]')
                .prop('disabled', true)
                .addClass('layui-btn-disabled');
        }

        $.post('/account/permissions', {}, function(res) {
            if (res && res.code === 0 && res.data) {
                permissions = res.data;
            }
            applyPermissionUi();
        });
    });
</script>
</body>
</html>
