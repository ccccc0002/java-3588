<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>系统配置</title>
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
        新增配置
    </button>
    <button class="pear-btn pear-btn-warming pear-btn-md" lay-event="license" style="margin-left: 8px;">
        授权管理
    </button>
    <button class="pear-btn pear-btn-normal pear-btn-md" lay-event="network" style="margin-left: 8px;">
        网络管理
    </button>
    <button class="pear-btn pear-btn-danger pear-btn-md" lay-event="scheduler" style="margin-left: 8px;">
        调度配置
    </button>
    <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="branding" style="margin-left: 8px;">
        品牌配置
    </button>
</script>

<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
    <a href="#" style="color: #DD4A68; margin-left: 15px;" lay-event="remove">删除</a>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'jquery', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let permissions = {can_write_system: false};
        const CONFIG_NAME_MAP = {
            infer_backend_type: '推理后端类型',
            infer_service_url: '推理服务地址',
            infer_timeout_ms: '推理超时（毫秒）',
            infer_retry_count: '推理重试次数',
            media_server_type: '流媒体服务类型',
            zlm_enable: '启用 ZLMediaKit',
            video_ports: '视频端口',
            zlm_schema: 'ZLM 协议',
            runtime_bootstrap_token: '运行时启动令牌',
            runtime_token_ttl_sec: '运行时令牌有效期（秒）',
            media_ffmpeg_bin: 'FFmpeg 可执行文件',
            zlm_host_public: 'ZLM 公网地址',
            zlm_host_inner: 'ZLM 内网地址',
            zlm_http_port: 'ZLM HTTP 端口',
            zlm_rtmp_port: 'ZLM RTMP 端口',
            zlm_app: 'ZLM 应用名',
            wsUrl: 'WebSocket 地址'
        };

        function resolveConfigName(row) {
            const tag = ((row && row.tag) || '').trim();
            const mapped = CONFIG_NAME_MAP[tag];
            if (mapped && mapped.length > 0) {
                return mapped;
            }
            return (row && row.name) || tag || '-';
        }

        let cols = [[
            { title: '配置名称', field: 'name', templet: function(d) { return resolveConfigName(d); } },
            { title: '配置标识', field: 'tag' },
            { title: '配置值', field: 'val' },
            { title: '操作', toolbar: '#table-actions', align: 'left', width: 130, fixed: 'right' }
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
                title: '刷新',
                layEvent: 'refresh',
                icon: 'layui-icon-refresh'
            }]
        });

        table.on('tool(table)', function(obj) {
            if (!permissions.can_write_system) {
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
                if (!permissions.can_write_system) {
                    popup.failure('无权限操作');
                    return;
                }
                window.addForm();
            } else if (obj.event === 'license') {
                if (!permissions.can_write_system) {
                    popup.failure('无权限操作');
                    return;
                }
                window.openLicense();
            } else if (obj.event === 'network') {
                if (!permissions.can_write_system) {
                    popup.failure('无权限操作');
                    return;
                }
                window.openNetwork();
            } else if (obj.event === 'scheduler') {
                if (!permissions.can_write_system) {
                    popup.failure('无权限操作');
                    return;
                }
                window.openScheduler();
            } else if (obj.event === 'branding') {
                if (!permissions.can_write_system) {
                    popup.failure('无权限操作');
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
                title: '新增配置',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/config/form'
            });
        };

        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: '编辑配置',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/config/form?id=' + obj.data.id
            });
        };

        window.openLicense = function() {
            layer.open({
                type: 2,
                title: '授权管理',
                shade: 0.1,
                area: ['70%', '70%'],
                content: '/config/license'
            });
        };

        window.openNetwork = function() {
            layer.open({
                type: 2,
                title: '网络管理',
                shade: 0.1,
                area: ['80%', '80%'],
                content: '/config/network'
            });
        };

        window.openScheduler = function() {
            layer.open({
                type: 2,
                title: '调度配置',
                shade: 0.1,
                area: ['70%', '78%'],
                content: '/config/scheduler'
            });
        };

        window.openBranding = function() {
            layer.open({
                type: 2,
                title: '品牌配置',
                shade: 0.1,
                area: ['72%', '82%'],
                content: '/config/branding'
            });
        };

        window.refreshTable = function() {
            table.reload('table');
        };

        window.removeData = function(obj) {
            layer.confirm('确定删除该配置吗？', {icon: 3, title: '提示'}, function(index) {
                layer.close(index);
                let loading = layer.load(2);
                $.post('/config/delete', {'id': obj.data.id}, function(res) {
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
