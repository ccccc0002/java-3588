<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>告警管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <form class="layui-form" action="">
                    <div class="layui-form-item">
                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">摄像头</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" name="cameraId">
                                    <option value="">-All-</option>
                                    <#list cameraList as item>
                                        <option value="${(item.id)!''}">${(item.name!'')}</option>
                                    </#list>
                                </select>
                            </div>
                        </div>

                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">算法</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" name="algorithmId">
                                    <option value="">-All-</option>
                                    <#list algorithmList as item>
                                        <option value="${(item.id)!''}">${(item.name!'')}</option>
                                    </#list>
                                </select>
                            </div>
                        </div>

                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">告警类型</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" name="type">
                                    <option value="">-All-</option>
                                    <#list typeList as item>
                                        <option value="${(item.id)!''}">${(item.name!'')}</option>
                                    </#list>
                                </select>
                            </div>
                        </div>
                    </div>

                    <div class="layui-form-item">
                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">快捷时间</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" id="quickRange" name="quickRange">
                                    <option value="">自定义</option>
                                    <option value="today">今日</option>
                                    <option value="24h">最近24小时</option>
                                    <option value="7d">最近7天</option>
                                    <option value="30d">最近30天</option>
                                </select>
                            </div>
                        </div>

                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">开始时间</label>
                            <div class="layui-input-inline" style="width: 180px;">
                                <input type="datetime-local" class="layui-input" id="startTime" name="startTime">
                            </div>
                        </div>

                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">结束时间</label>
                            <div class="layui-input-inline" style="width: 180px;">
                                <input type="datetime-local" class="layui-input" id="endTime" name="endTime">
                            </div>
                        </div>

                        <div class="layui-form-item layui-inline">
                            <button class="pear-btn pear-btn-md pear-btn-primary" lay-submit lay-filter="query">
                                <i class="layui-icon layui-icon-search"></i>查询
                            </button>
                            <button type="button" class="pear-btn pear-btn-md" id="resetFilterBtn">
                                <i class="layui-icon layui-icon-refresh"></i>重置
                            </button>
                            <button type="button" class="pear-btn pear-btn-md pear-btn-warming" id="managePushTargetsBtn">
                                <i class="layui-icon layui-icon-set"></i>HTTP推送目标
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>

        <div class="layui-card">
            <div class="layui-card-body">
                <table id="table" lay-filter="table"></table>
            </div>
        </div>
    </div>
</div>

<script type="text/html" id="table-toolbar">告警列表</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="detail">详情</a>
    <a href="#" style="color: #409EFF; margin-left: 10px;" lay-event="push">推送</a>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let form = layui.form;
        let layer = layui.layer;
        let canManagePushTargets = false;

        function applyPermissionState() {
            if (canManagePushTargets) {
                $('#managePushTargetsBtn').removeClass('layui-btn-disabled').prop('disabled', false).attr('title', '');
            } else {
                $('#managePushTargetsBtn').addClass('layui-btn-disabled').prop('disabled', true).attr('title', '当前角色无权限');
            }
        }

        function loadPermissionMatrix(done) {
            $.post('/account/permissions', {}, function(res) {
                if (res && res.code === 0 && res.data) {
                    canManagePushTargets = !!res.data.can_manage_push_targets;
                } else {
                    canManagePushTargets = false;
                }
            }).always(function() {
                applyPermissionState();
                if (typeof done === 'function') {
                    done();
                }
            });
        }

        function formatForDateTimeLocal(date) {
            let y = date.getFullYear();
            let m = String(date.getMonth() + 1).padStart(2, '0');
            let d = String(date.getDate()).padStart(2, '0');
            let hh = String(date.getHours()).padStart(2, '0');
            let mm = String(date.getMinutes()).padStart(2, '0');
            return y + '-' + m + '-' + d + 'T' + hh + ':' + mm;
        }

        function applyQuickRange(rangeValue) {
            let now = new Date();
            let start = null;
            let end = now;
            if (rangeValue === 'today') {
                start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
            } else if (rangeValue === '24h') {
                start = new Date(now.getTime() - 24 * 60 * 60 * 1000);
            } else if (rangeValue === '7d') {
                start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
            } else if (rangeValue === '30d') {
                start = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
            }
            if (start) {
                $('#startTime').val(formatForDateTimeLocal(start));
                $('#endTime').val(formatForDateTimeLocal(end));
            }
        }

        function queryTable(where) {
            table.reload('table', { where: where || {} });
        }

        function escapeHtml(text) {
            return String(text || '')
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        }

        function normalizeBool(value, defaultValue) {
            if (typeof value === 'boolean') {
                return value;
            }
            if (value === null || value === undefined || value === '') {
                return !!defaultValue;
            }
            return value === 'true' || value === '1' || value === 1 || value === 'yes';
        }

        function formatPushSummary(res) {
            let data = res && res.data ? res.data : null;
            if (!data || data.target_count === undefined) {
                return '';
            }
            return '总目标: ' + (data.target_count || 0)
                + '，成功: ' + (data.success_count || 0)
                + '，失败: ' + (data.failed_count || 0);
        }

        function openPushTargetEditor(target, onSaved) {
            let current = $.extend({
                id: '',
                name: '',
                url: '',
                bearer_token: '',
                enabled: true,
                include_image: false
            }, target || {});

            let content = ''
                + '<div style="padding: 16px 20px;">'
                + '  <div class="layui-form" lay-filter="pushTargetEditorForm">'
                + '    <div class="layui-form-item">'
                + '      <label class="layui-form-label">名称</label>'
                + '      <div class="layui-input-block">'
                + '        <input type="text" id="push-target-name" class="layui-input" placeholder="例如：安全平台A">'
                + '      </div>'
                + '    </div>'
                + '    <div class="layui-form-item">'
                + '      <label class="layui-form-label">URL</label>'
                + '      <div class="layui-input-block">'
                + '        <input type="text" id="push-target-url" class="layui-input" placeholder="http://127.0.0.1:9000/push">'
                + '      </div>'
                + '    </div>'
                + '    <div class="layui-form-item">'
                + '      <label class="layui-form-label">Bearer</label>'
                + '      <div class="layui-input-block">'
                + '        <input type="text" id="push-target-bearer" class="layui-input" placeholder="可选">'
                + '      </div>'
                + '    </div>'
                + '    <div class="layui-form-item">'
                + '      <div class="layui-input-block">'
                + '        <input type="checkbox" id="push-target-enabled" title="启用">'
                + '        <input type="checkbox" id="push-target-include-image" title="携带图片Base64">'
                + '      </div>'
                + '    </div>'
                + '  </div>'
                + '</div>';

            layer.open({
                type: 1,
                title: current.id ? '编辑推送目标' : '新增推送目标',
                area: ['560px', '360px'],
                content: content,
                btn: ['保存', '取消'],
                success: function(layero) {
                    let root = $(layero);
                    root.find('#push-target-name').val(current.name || '');
                    root.find('#push-target-url').val(current.url || '');
                    root.find('#push-target-bearer').val(current.bearer_token || '');
                    root.find('#push-target-enabled').prop('checked', normalizeBool(current.enabled, true));
                    root.find('#push-target-include-image').prop('checked', normalizeBool(current.include_image, false));
                    form.render('checkbox');
                },
                yes: function(index, layero) {
                    let root = $(layero);
                    let payload = {
                        id: current.id || '',
                        name: $.trim(root.find('#push-target-name').val() || ''),
                        url: $.trim(root.find('#push-target-url').val() || ''),
                        bearerToken: $.trim(root.find('#push-target-bearer').val() || ''),
                        enabled: root.find('#push-target-enabled').prop('checked'),
                        includeImage: root.find('#push-target-include-image').prop('checked')
                    };
                    if (!payload.url) {
                        popup.failure('推送URL不能为空');
                        return;
                    }
                    let loading = layer.load(2);
                    $.post('/report/push/targets/save', payload, function(res) {
                        if (res && res.code === 0) {
                            layer.close(index);
                            layer.msg('保存成功', { icon: 1, time: 1000 });
                            if (typeof onSaved === 'function') {
                                onSaved();
                            }
                        } else {
                            popup.failure((res && res.msg) || '保存失败');
                        }
                    }).always(function() {
                        layer.close(loading);
                    });
                }
            });
        }

        function renderPushTargetTable($container, targets) {
            if (!targets || targets.length === 0) {
                $container.html('<div style="padding: 18px; color: #666;">暂无推送目标</div>');
                return;
            }
            let rows = targets.map(function(item) {
                let enabledText = normalizeBool(item.enabled, true) ? '是' : '否';
                let includeImageText = normalizeBool(item.include_image, false) ? '是' : '否';
                let actionCell = '-';
                if (canManagePushTargets) {
                    actionCell = ''
                        + '<button class="pear-btn pear-btn-xs" data-action="edit" data-id="' + escapeHtml(item.id || '') + '">编辑</button>'
                        + '<button class="pear-btn pear-btn-xs pear-btn-danger" data-action="delete" data-id="' + escapeHtml(item.id || '') + '" style="margin-left:6px;">删除</button>';
                }
                return ''
                    + '<tr>'
                    + '  <td>' + escapeHtml(item.name || '-') + '</td>'
                    + '  <td><code>' + escapeHtml(item.url || '') + '</code></td>'
                    + '  <td>' + enabledText + '</td>'
                    + '  <td>' + includeImageText + '</td>'
                    + '  <td>' + actionCell + '</td>'
                    + '</tr>';
            }).join('');

            let toolbarHtml = canManagePushTargets
                ? '<button type="button" class="pear-btn pear-btn-sm pear-btn-primary" id="addPushTargetBtn">新增目标</button>'
                : '<span style="color:#999;">只读模式：无推送目标管理权限</span>';

            let html = ''
                + '<div style="padding: 10px 14px 16px;">'
                + '  <div style="margin-bottom: 10px;">'
                +      toolbarHtml
                + '  </div>'
                + '  <table class="layui-table" lay-size="sm">'
                + '    <colgroup><col width="140"><col><col width="70"><col width="110"><col width="160"></colgroup>'
                + '    <thead><tr><th>名称</th><th>URL</th><th>启用</th><th>带图片</th><th>操作</th></tr></thead>'
                + '    <tbody>' + rows + '</tbody>'
                + '  </table>'
                + '</div>';
            $container.html(html);
        }

        function openPushTargetManager() {
            layer.open({
                type: 1,
                title: 'HTTP推送目标管理',
                area: ['900px', '560px'],
                content: '<div id="push-target-manager"></div>',
                success: function(layero) {
                    let root = $(layero).find('#push-target-manager');
                    let currentTargets = [];

                    function reloadTargets() {
                        $.post('/report/push/targets', {}, function(res) {
                            if (res && res.code === 0) {
                                currentTargets = Array.isArray(res.data) ? res.data : [];
                                renderPushTargetTable(root, currentTargets);
                            } else {
                                popup.failure((res && res.msg) || '读取推送目标失败');
                            }
                        });
                    }

                    root.on('click', '#addPushTargetBtn', function() {
                        if (!canManagePushTargets) {
                            popup.failure('无权限操作');
                            return;
                        }
                        openPushTargetEditor(null, reloadTargets);
                    });

                    root.on('click', 'button[data-action="edit"]', function() {
                        if (!canManagePushTargets) {
                            popup.failure('无权限操作');
                            return;
                        }
                        let id = $(this).data('id');
                        let target = currentTargets.find(function(item) {
                            return String(item.id || '') === String(id || '');
                        });
                        if (!target) {
                            popup.failure('目标不存在或已被删除');
                            return;
                        }
                        openPushTargetEditor(target, reloadTargets);
                    });

                    root.on('click', 'button[data-action="delete"]', function() {
                        if (!canManagePushTargets) {
                            popup.failure('无权限操作');
                            return;
                        }
                        let id = $(this).data('id');
                        layer.confirm('确认删除该推送目标？', { icon: 3, title: '提示' }, function(confirmIndex) {
                            layer.close(confirmIndex);
                            let loading = layer.load(2);
                            $.post('/report/push/targets/delete', { id: id }, function(res) {
                                if (res && res.code === 0) {
                                    layer.msg('删除成功', { icon: 1, time: 1000 });
                                    reloadTargets();
                                } else {
                                    popup.failure((res && res.msg) || '删除失败');
                                }
                            }).always(function() {
                                layer.close(loading);
                            });
                        });
                    });

                    reloadTargets();
                }
            });
        }

        form.on('submit(query)', function(data) {
            if (data.field.quickRange) {
                applyQuickRange(data.field.quickRange);
                data.field.startTime = $('#startTime').val();
                data.field.endTime = $('#endTime').val();
            }
            queryTable(data.field);
            return false;
        });

        $('#quickRange').on('change', function() {
            let value = ($(this).val() || '').trim();
            if (!value) {
                return;
            }
            applyQuickRange(value);
        });

        $('#resetFilterBtn').on('click', function() {
            $('select[name="cameraId"]').val('');
            $('select[name="algorithmId"]').val('');
            $('select[name="type"]').val('');
            $('#quickRange').val('');
            $('#startTime').val('');
            $('#endTime').val('');
            form.render('select');
            queryTable({});
        });

        $('#managePushTargetsBtn').on('click', function() {
            if (!canManagePushTargets) {
                popup.failure('无权限操作');
                return;
            }
            openPushTargetManager();
        });

        let cols = [[
            { title: '摄像头名称', field: 'cameraName' },
            { title: '算法名称', field: 'algorithmName' },
            { title: '告警类型', field: 'typeName' },
            { title: '创建时间', field: 'createdStr', width: 180 },
            { title: '操作', toolbar: '#table-actions', align: 'left', width: 140, fixed: 'right' }
        ]];

        table.render({
            elem: '#table',
            url: '/report/listPage',
            method: 'post',
            page: true,
            cols: cols,
            skin: 'line',
            height: 'full-190',
            toolbar: '#table-toolbar',
            defaultToolbar: [{
                title: 'Refresh',
                layEvent: 'refresh',
                icon: 'layui-icon-refresh'
            }]
        });

        table.on('tool(table)', function(obj) {
            if (obj.event === 'detail') {
                parent.layui.admin.addTab(obj.data.id, (obj.data.cameraName || '告警') + '-详情', '/report/detail?id=' + obj.data.id);
            } else if (obj.event === 'push') {
                if (!canManagePushTargets) {
                    popup.failure('无权限操作');
                    return;
                }
                window.pushData(obj);
            }
        });

        table.on('toolbar(table)', function(obj) {
            if (obj.event === 'refresh') {
                queryTable({});
            }
        });

        window.pushData = function(obj) {
            if (!canManagePushTargets) {
                popup.failure('无权限操作');
                return;
            }
            layer.confirm('确定推送吗?', {
                icon: 3,
                title: '提示'
            }, function(index) {
                layer.close(index);
                let loading = layer.load(2);
                $.post('/report/pushData', { id: obj.data.id }, function(res) {
                    layer.close(loading);
                    let summary = formatPushSummary(res);
                    if (res && res.code === 0) {
                        layer.msg(summary || '推送成功', { icon: 1, time: 1500 });
                    } else {
                        popup.failure(((res && res.msg) || '推送失败') + (summary ? ('；' + summary) : ''));
                    }
                });
            });
        };

        loadPermissionMatrix();
    });
</script>
</body>
</html>
