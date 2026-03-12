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
                <input id="algo-package-file" type="file" accept=".zip" style="display:none;" />
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
    <button class="pear-btn pear-btn-warming pear-btn-md" lay-event="importPackage" style="margin-left: 8px;">
        <i class="layui-icon layui-icon-upload-drag"></i>
        导入算法包
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
        let canWriteSystem = false;

        function loadPermissionMatrix(done) {
            $.post('/account/permissions', {}, function(res) {
                if (res && res.code === 0 && res.data) {
                    canWriteSystem = !!res.data.can_write_system;
                } else {
                    canWriteSystem = false;
                }
            }).always(function() {
                if (typeof done === 'function') {
                    done();
                }
            });
        }

        function guardWriteAction() {
            if (!canWriteSystem) {
                popup.failure('无权限操作');
                return false;
            }
            return true;
        }

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
                return util.toDateString(d.updatedAt);
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

        table.on('tool(table)', function(obj) {
            if(obj.event === 'remove') {
                window.removeData(obj);
            } else if(obj.event === 'edit') {
                window.editMetadata(obj);
            }
        });

        table.on('toolbar(table)', function(obj) {
            if(obj.event === 'add') {
                window.addForm();
            } else if(obj.event === 'importPackage') {
                window.importPackage();
            } else if(obj.event === 'refresh') {
                window.refreshTable();
            }
        });

        window.addForm = function() {
            if (!guardWriteAction()) {
                return;
            }
            layer.open({
                type: 2,
                title: '新增算法',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/algorithm/form'
            });
        }

        window.editMetadata = function(obj) {
            if (!guardWriteAction()) {
                return;
            }
            $.post('/algorithm/detail', {id: obj.data.id}, function(res) {
                if (!res || res.code !== 0) {
                    popup.failure((res && res.msg) || 'Load detail failed');
                    return;
                }
                let detail = res.data || {};
                let params = window.safeParseJson(detail.params, {});
                let labelAliases = params.label_aliases_zh || {};
                let aliasesJson = JSON.stringify(labelAliases, null, 2);

                let content = '<div style="padding:16px;">'
                    + '<div style="margin-bottom:12px;"><label style="display:block;margin-bottom:6px;">Name</label><input id="algo-edit-name" class="layui-input" /></div>'
                    + '<div style="margin-bottom:12px;"><label style="display:block;margin-bottom:6px;">Description</label><textarea id="algo-edit-description" class="layui-textarea" style="min-height:88px;"></textarea></div>'
                    + '<div><label style="display:block;margin-bottom:6px;">Label aliases (JSON)</label><textarea id="algo-edit-aliases" class="layui-textarea" style="min-height:180px;"></textarea></div>'
                    + '</div>';

                layer.open({
                    type: 1,
                    title: 'Edit package metadata',
                    area: ['640px', '560px'],
                    content: content,
                    btn: ['Save', 'Cancel'],
                    success: function() {
                        $('#algo-edit-name').val(detail.name || '');
                        $('#algo-edit-description').val(params.description || '');
                        $('#algo-edit-aliases').val(aliasesJson || '{}');
                    },
                    yes: function(index) {
                        let name = $('#algo-edit-name').val();
                        let description = $('#algo-edit-description').val();
                        let labelAliasesZh = $('#algo-edit-aliases').val();
                        let loading = layer.load(2);
                        $.post('/algorithm/package/updateMetadata', {
                            id: detail.id,
                            name: name,
                            description: description,
                            labelAliasesZh: labelAliasesZh
                        }, function(updateRes) {
                            layer.close(loading);
                            if (updateRes && updateRes.code === 0) {
                                layer.close(index);
                                popup.success('Metadata updated');
                                window.refreshTable();
                                return;
                            }
                            popup.failure((updateRes && updateRes.msg) || 'Metadata update failed');
                        });
                    }
                });
            });
        }

        window.safeParseJson = function(text, fallback) {
            if (!text) {
                return fallback;
            }
            try {
                return JSON.parse(text);
            } catch (e) {
                return fallback;
            }
        }

        window.refreshTable = function() {
            table.reload('table');
        }

        window.importPackage = function() {
            if (!guardWriteAction()) {
                return;
            }
            let input = document.getElementById('algo-package-file');
            if (!input) {
                return;
            }
            input.value = '';
            input.click();
        }

        $('#algo-package-file').on('change', function() {
            let file = this.files && this.files[0];
            if (!file) {
                return;
            }
            window.submitPackageFile(file);
        });

        window.submitPackageFile = function(file) {
            let formData = new FormData();
            formData.append('file', file);
            let loading = layer.load(2);
            $.ajax({
                url: '/algorithm/package/import',
                type: 'POST',
                data: formData,
                processData: false,
                contentType: false,
                success: function(res) {
                    layer.close(loading);
                    if (res && res.code === 0) {
                        popup.success('Package imported');
                        window.refreshTable();
                        return;
                    }
                    popup.failure((res && res.msg) || 'Package import failed');
                },
                error: function() {
                    layer.close(loading);
                    popup.failure('Package import failed');
                }
            });
        }

        window.removeData = function (obj) {
            if (!guardWriteAction()) {
                return;
            }
            layer.confirm('确定删除该算法吗?', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                let loading = layer.load(2);
                $.post('/algorithm/delete', {'id': obj.data.id}, function(res) {
                    layer.close(loading);
                    if (res && res.code === 0) {
                        layer.msg('操作成功', {icon:1, time:1000}, function() {
                            window.refreshTable();
                        });
                        return;
                    }
                    if (res && res.code === 409) {
                        layer.confirm('该算法已绑定摄像头，是否强制删除并自动解除绑定？', {
                            icon: 3,
                            title: '二次确认'
                        }, function (idx2) {
                            layer.close(idx2);
                            let loading2 = layer.load(2);
                            $.post('/algorithm/forceDelete', {'id': obj.data.id}, function(forceRes) {
                                layer.close(loading2);
                                if (forceRes && forceRes.code === 0) {
                                    popup.success('Force delete success');
                                    window.refreshTable();
                                    return;
                                }
                                popup.failure((forceRes && forceRes.msg) || '强制删除失败');
                            });
                        });
                        return;
                    }
                    popup.failure((res && res.msg) || 'Delete failed');
                });
            });
        }

        loadPermissionMatrix();
    })
</script>
</html>
