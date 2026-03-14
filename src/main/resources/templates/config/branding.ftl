<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>品牌配置</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .layui-form-label { width: 180px; }
        .layui-input-block { margin-left: 210px; }
        .preview-box {
            margin-top: 10px;
            border: 1px dashed #dcdfe6;
            border-radius: 4px;
            min-height: 120px;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
            background: #fafafa;
        }
        .preview-box img {
            max-width: 100%;
            max-height: 220px;
            display: block;
        }
        .upload-line {
            display: flex;
            gap: 8px;
            align-items: center;
        }
        .upload-line input[type=file] {
            display: none;
        }
    </style>
</head>
<body class="pear-container">
<div class="layui-card">
    <div class="layui-card-header">品牌配置</div>
    <div class="layui-card-body">
        <form class="layui-form" lay-filter="branding-form">
            <div class="layui-form-item">
                <label class="layui-form-label">软件标题</label>
                <div class="layui-input-block">
                    <input type="text" name="brand_title" id="brand_title" autocomplete="off" class="layui-input" placeholder="AI视频监控管理系统">
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">LOGO地址</label>
                <div class="layui-input-block">
                    <input type="text" name="brand_logo_url" id="brand_logo_url" autocomplete="off" class="layui-input" placeholder="/static/admin/images/logo.png 或 /image/stream?fileName=...">
                    <div class="upload-line" style="margin-top: 8px;">
                        <button type="button" class="pear-btn pear-btn-primary pear-btn-sm" id="btnSelectLogo">上传LOGO</button>
                        <input type="file" id="logoFile" accept=".png,.jpg,.jpeg">
                    </div>
                    <div class="preview-box"><img id="logo_preview" alt="logo preview" /></div>
                </div>
            </div>
            <div class="layui-form-item">
                <label class="layui-form-label">登录页背景地址</label>
                <div class="layui-input-block">
                    <input type="text" name="login_background_url" id="login_background_url" autocomplete="off" class="layui-input" placeholder="/static/admin/images/background.svg 或 /image/stream?fileName=...">
                    <div class="upload-line" style="margin-top: 8px;">
                        <button type="button" class="pear-btn pear-btn-primary pear-btn-sm" id="btnSelectBackground">上传背景图</button>
                        <input type="file" id="backgroundFile" accept=".png,.jpg,.jpeg">
                    </div>
                    <div class="preview-box"><img id="background_preview" alt="background preview" /></div>
                </div>
            </div>
            <div class="layui-form-item">
                <div class="layui-input-block">
                    <button class="pear-btn pear-btn-primary" lay-submit lay-filter="saveBranding">保存</button>
                    <button type="button" class="pear-btn" id="btnReload">刷新</button>
                </div>
            </div>
        </form>
    </div>
</div>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['form', 'jquery', 'popup'], function () {
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;
        let canWriteSystem = false;

        function setWritable(writable) {
            canWriteSystem = !!writable;
            if (canWriteSystem) {
                return;
            }
            $('input,button').prop('disabled', true);
            form.render();
        }

        function setPreview(selector, url) {
            let node = $(selector);
            if (url) {
                node.attr('src', url).show();
            } else {
                node.attr('src', '').hide();
            }
        }

        function loadBranding() {
            $.post('/config/branding/info', {}, function (res) {
                if (!res || res.code !== 0 || !res.data) {
                    popup.failure((res && res.msg) || '加载品牌配置失败');
                    return;
                }
                let data = res.data;
                form.val('branding-form', {
                    brand_title: data.brand_title || '',
                    brand_logo_url: data.brand_logo_url || '',
                    login_background_url: data.login_background_url || ''
                });
                setPreview('#logo_preview', data.brand_logo_url || '');
                setPreview('#background_preview', data.login_background_url || '');
            });
        }

        function uploadImage(fileInput, done) {
            let files = fileInput.files || [];
            if (!files.length) {
                return;
            }
            let formData = new FormData();
            formData.append('file', files[0]);
            let loading = layer.load(2);
            $.ajax({
                url: '/image/upload',
                method: 'POST',
                data: formData,
                processData: false,
                contentType: false
            }).done(function (res) {
                if (!res || res.code !== 0 || !res.data) {
                    popup.failure((res && res.msg) || '上传失败');
                    return;
                }
                let imageUrl = '/image/stream?fileName=' + encodeURIComponent(res.data);
                done(imageUrl);
            }).fail(function () {
                popup.failure('上传失败');
            }).always(function () {
                layer.close(loading);
            });
        }

        form.on('submit(saveBranding)', function (data) {
            if (!canWriteSystem) {
                popup.failure('无权限操作');
                return false;
            }
            let loading = layer.load(2);
            $.post('/config/branding/save', data.field, function (res) {
                layer.close(loading);
                if (res && res.code === 0) {
                    popup.success('品牌配置保存成功，请刷新浏览器生效');
                    loadBranding();
                } else {
                    popup.failure((res && res.msg) || '保存品牌配置失败');
                }
            }).fail(function () {
                layer.close(loading);
                popup.failure('保存品牌配置失败');
            });
            return false;
        });

        $('#brand_logo_url').on('input', function () {
            setPreview('#logo_preview', $(this).val().trim());
        });
        $('#login_background_url').on('input', function () {
            setPreview('#background_preview', $(this).val().trim());
        });

        $('#btnSelectLogo').on('click', function () {
            if (!canWriteSystem) {
                popup.failure('无权限操作');
                return;
            }
            $('#logoFile').trigger('click');
        });
        $('#btnSelectBackground').on('click', function () {
            if (!canWriteSystem) {
                popup.failure('无权限操作');
                return;
            }
            $('#backgroundFile').trigger('click');
        });

        $('#logoFile').on('change', function () {
            uploadImage(this, function (url) {
                $('#brand_logo_url').val(url);
                setPreview('#logo_preview', url);
                form.render();
            });
        });
        $('#backgroundFile').on('change', function () {
            uploadImage(this, function (url) {
                $('#login_background_url').val(url);
                setPreview('#background_preview', url);
                form.render();
            });
        });

        $('#btnReload').on('click', loadBranding);

        $.post('/account/permissions', {}, function (res) {
            if (res && res.code === 0 && res.data) {
                setWritable(!!res.data.can_write_system);
            } else {
                setWritable(false);
            }
        }).always(function () {
            loadBranding();
        });
    });
</script>
</body>
</html>
