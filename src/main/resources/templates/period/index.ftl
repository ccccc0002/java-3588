<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>时段配置</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <script type="text/html" id="periodTpl">
        {{ d.startText }} - {{ d.endText }}
    </script>
</head>
<body class="pear-container1">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <table id="table" lay-filter="table"></table>
            </div>
        </div>
    </div>
</div>

<div class="bottom">
    <div class="button-container">
        <button class="pear-btn pear-btn-sm" id="close-layer">
            <i class="layui-icon layui-icon-close"></i>
            关闭
        </button>
    </div>
</div>
</body>
<script type="text/html" id="table-toolbar">
    <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="add">
        <i class="layui-icon layui-icon-add-1"></i>
        新增告警时段
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
        let permissions = {can_write_system: false};

        //
        let cols = [{
            title: '摄像头名称',
            field: 'cameraName'
        }, {
            title: '算法名称',
            field: 'algorithmName'
        }, {
            title: '告警时段',
            field: 'period'
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
            url: '/report/period/listData',
            method: 'post',
            where: {
                'cameraId': '${cameraId!''}'
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
                if(!guardWriteAction()) {
                    return;
                }
                window.removeData(obj);
            } else if(obj.event == 'edit') {
                if(!guardWriteAction()) {
                    return;
                }
                window.editForm(obj);
            }
        });

        //
        table.on('toolbar(table)', function(obj) {
            if(obj.event == 'add') {
                if(!guardWriteAction()) {
                    return;
                }
                window.addForm();
            } else if(obj.event == 'refresh') {
                window.refreshTable();
            }
        });

        //
        window.addForm = function() {
            layer.open({
                type: 2,
                title: '新增告警时段',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/report/period/form?cameraId=${cameraId!''}'
            });
        }

        //
        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: '编辑告警时段',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/report/period/form?cameraId=${cameraId!''}&id=' + obj.data.id
            });
        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }

        //
        window.removeData = function (obj) {
            if(!guardWriteAction()) {
                return;
            }
            layer.confirm('确定删除该数据吗?', {
                icon: 3,
                title: '警告'
            }, function (index) {
                layer.close(index);
                var loading = layer.load(2);
                $.post('/report/period/delete', {'id': obj.data['id']}, function(res) {
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

        function guardWriteAction() {
            if(permissions.can_write_system) {
                return true;
            }
            popup.failure('permission denied');
            return false;
        }

        function applyPermissionUi() {
            if(permissions.can_write_system) {
                return;
            }
            $('button[lay-event=add]').prop('disabled', true).addClass('layui-btn-disabled');
            $('a[lay-event=edit],a[lay-event=remove]').addClass('layui-disabled').css('pointer-events', 'none');
        }

        $.post('/account/permissions', {}, function(res) {
            if(res && res.code == 0 && res.data) {
                permissions = res.data;
            }
            applyPermissionUi();
        });

        $('#close-layer').click(function() {
            parent.layui.table.reload('table');
            parent.layer.close(parent.layer.getFrameIndex(window.name));
        });
    })
</script>
</html>
