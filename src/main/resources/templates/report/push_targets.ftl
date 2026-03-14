<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>HTTP 推送目标管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <div style="margin-bottom: 12px;">
                    <button type="button" class="pear-btn pear-btn-primary pear-btn-sm" id="addPushTargetBtn">
                        <i class="layui-icon layui-icon-add-1"></i>新增目标
                    </button>
                    <button type="button" class="pear-btn pear-btn-sm" id="refreshPushTargetBtn">
                        <i class="layui-icon layui-icon-refresh"></i>刷新
                    </button>
                    <span id="permissionHint" style="margin-left: 12px; color: #999;"></span>
                </div>
                <table class="layui-table" lay-size="sm">
                    <colgroup>
                        <col width="140">
                        <col>
                        <col width="90">
                        <col width="110">
                        <col width="90">
                        <col width="160">
                        <col width="180">
                    </colgroup>
                    <thead>
                    <tr>
                        <th>名称</th>
                        <th>URL</th>
                        <th>启用</th>
                        <th>附带图片</th>
                        <th>重试</th>
                        <th>鉴权文件</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="pushTargetsBody">
                    <tr>
                        <td colspan="7" style="text-align: center; color: #999;">加载中...</td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['jquery', 'form', 'popup'], function() {
        let $ = layui.jquery;
        let form = layui.form;
        let popup = layui.popup;
        let layer = layui.layer;
        let canManagePushTargets = false;
        let currentTargets = [];

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

        function normalizeRetryCount(value) {
            let parsed = parseInt(value, 10);
            if (isNaN(parsed) || parsed <= 0) {
                return 1;
            }
            if (parsed > 10) {
                return 10;
            }
            return parsed;
        }

        function applyPermissionState() {
            if (canManagePushTargets) {
                $('#addPushTargetBtn').removeClass('layui-btn-disabled').prop('disabled', false);
                $('#permissionHint').text('');
            } else {
                $('#addPushTargetBtn').addClass('layui-btn-disabled').prop('disabled', true);
                $('#permissionHint').text('只读模式：无推送目标管理权限');
            }
        }

        function renderRows() {
            let body = $('#pushTargetsBody');
            if (!currentTargets || currentTargets.length === 0) {
                body.html('<tr><td colspan="7" style="text-align:center; color:#999;">暂无推送目标</td></tr>');
                return;
            }
            let rows = currentTargets.map(function(item) {
                let enabledText = normalizeBool(item.enabled, true) ? '是' : '否';
                let includeImageText = normalizeBool(item.include_image, false) ? '是' : '否';
                let retryCount = normalizeRetryCount(item.retry_count);
                let authFile = item.auth_file || '-';
                let actions = '-';
                if (canManagePushTargets) {
                    actions = ''
                        + '<button class="pear-btn pear-btn-xs" data-action="edit" data-id="' + escapeHtml(item.id || '') + '">编辑</button>'
                        + '<button class="pear-btn pear-btn-xs pear-btn-danger" data-action="delete" data-id="' + escapeHtml(item.id || '') + '" style="margin-left:6px;">删除</button>';
                }
                return ''
                    + '<tr>'
                    + '<td>' + escapeHtml(item.name || '-') + '</td>'
                    + '<td><code>' + escapeHtml(item.url || '') + '</code></td>'
                    + '<td>' + enabledText + '</td>'
                    + '<td>' + includeImageText + '</td>'
                    + '<td>' + retryCount + '</td>'
                    + '<td><code>' + escapeHtml(authFile) + '</code></td>'
                    + '<td>' + actions + '</td>'
                    + '</tr>';
            }).join('');
            body.html(rows);
        }

        function reloadTargets() {
            $.post('/report/push/targets', {}, function(res) {
                if (res && res.code === 0) {
                    currentTargets = Array.isArray(res.data) ? res.data : [];
                    renderRows();
                } else {
                    popup.failure((res && res.msg) || '读取推送目标失败');
                }
            });
        }

        function openEditor(target) {
            let current = $.extend({
                id: '',
                name: '',
                url: '',
                bearer_token: '',
                enabled: true,
                include_image: false,
                auth_file: '',
                retry_count: 1
            }, target || {});

            let content = ''
                + '<div style="padding: 16px 20px;">'
                + '  <div class="layui-form" lay-filter="pushTargetEditorForm">'
                + '    <div class="layui-form-item">'
                + '      <label class="layui-form-label">名称</label>'
                + '      <div class="layui-input-block">'
                + '        <input type="text" id="push-target-name" class="layui-input" placeholder="例如：安防平台A">'
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
                + '        <input type="text" id="push-target-bearer" class="layui-input" placeholder="可选：直接填写 token">'
                + '      </div>'
                + '    </div>'
                + '    <div class="layui-form-item">'
                + '      <label class="layui-form-label">鉴权文件</label>'
                + '      <div class="layui-input-block">'
                + '        <input type="text" id="push-target-auth-file" class="layui-input" placeholder="可选：token 文件绝对路径">'
                + '      </div>'
                + '    </div>'
                + '    <div class="layui-form-item">'
                + '      <label class="layui-form-label">重试次数</label>'
                + '      <div class="layui-input-block">'
                + '        <input type="number" id="push-target-retry-count" class="layui-input" min="1" max="10" step="1" value="1">'
                + '      </div>'
                + '    </div>'
                + '    <div class="layui-form-item">'
                + '      <div class="layui-input-block">'
                + '        <input type="checkbox" id="push-target-enabled" title="启用">'
                + '        <input type="checkbox" id="push-target-include-image" title="附带图片 Base64">'
                + '      </div>'
                + '    </div>'
                + '  </div>'
                + '</div>';

            layer.open({
                type: 1,
                title: current.id ? '编辑推送目标' : '新增推送目标',
                area: ['620px', '520px'],
                content: content,
                btn: ['保存', '取消'],
                success: function(layero) {
                    let root = $(layero);
                    root.find('#push-target-name').val(current.name || '');
                    root.find('#push-target-url').val(current.url || '');
                    root.find('#push-target-bearer').val(current.bearer_token || '');
                    root.find('#push-target-auth-file').val(current.auth_file || '');
                    root.find('#push-target-retry-count').val(normalizeRetryCount(current.retry_count));
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
                        authFile: $.trim(root.find('#push-target-auth-file').val() || ''),
                        retryCount: normalizeRetryCount(root.find('#push-target-retry-count').val()),
                        enabled: root.find('#push-target-enabled').prop('checked'),
                        includeImage: root.find('#push-target-include-image').prop('checked')
                    };
                    if (!payload.url) {
                        popup.failure('推送 URL 不能为空');
                        return;
                    }
                    let loading = layer.load(2);
                    $.post('/report/push/targets/save', payload, function(res) {
                        if (res && res.code === 0) {
                            layer.close(index);
                            layer.msg('保存成功', { icon: 1, time: 1000 });
                            reloadTargets();
                        } else {
                            popup.failure((res && res.msg) || '保存失败');
                        }
                    }).always(function() {
                        layer.close(loading);
                    });
                }
            });
        }

        $('#refreshPushTargetBtn').on('click', function() {
            reloadTargets();
        });

        $('#addPushTargetBtn').on('click', function() {
            if (!canManagePushTargets) {
                popup.failure('无权限操作');
                return;
            }
            openEditor(null);
        });

        $('#pushTargetsBody').on('click', 'button[data-action="edit"]', function() {
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
            openEditor(target);
        });

        $('#pushTargetsBody').on('click', 'button[data-action="delete"]', function() {
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

        $.post('/account/permissions', {}, function(res) {
            if (res && res.code === 0 && res.data) {
                canManagePushTargets = !!res.data.can_manage_push_targets;
            } else {
                canManagePushTargets = false;
            }
            applyPermissionState();
            reloadTargets();
        }).fail(function() {
            canManagePushTargets = false;
            applyPermissionState();
            reloadTargets();
        });
    });
</script>
</body>
</html>
