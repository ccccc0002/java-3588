package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.yihecode.camera.ai.service.AlgorithmPackageLifecycleService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@SaCheckLogin
@Controller
@RequestMapping({"/algorithm"})
public class AlgorithmPackageLifecycleController {

    @Autowired
    private AlgorithmPackageLifecycleService algorithmPackageLifecycleService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    @PostMapping({"/package/import"})
    @ResponseBody
    public JsonResult importPackage(MultipartFile file) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        try {
            Object result = algorithmPackageLifecycleService.importPackage(file);
            operationLogService.record("algorithm:package_import", "algorithm", true, "algorithm package imported", file == null ? "" : file.getOriginalFilename());
            return JsonResultUtils.success(result);
        } catch (Exception ex) {
            operationLogService.record("algorithm:package_import", "algorithm", false, "algorithm package import failed", ex.getMessage());
            return JsonResultUtils.fail(ex.getMessage());
        }
    }

    @PostMapping({"/forceDelete"})
    @ResponseBody
    public JsonResult forceDelete(Long id) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        try {
            Object result = algorithmPackageLifecycleService.forceDelete(id);
            operationLogService.record("algorithm:force_delete", "algorithmId=" + id, true, "algorithm force deleted", "");
            return JsonResultUtils.success(result);
        } catch (Exception ex) {
            operationLogService.record("algorithm:force_delete", "algorithmId=" + id, false, "algorithm force delete failed", ex.getMessage());
            return JsonResultUtils.fail(ex.getMessage());
        }
    }

    @PostMapping({"/package/updateMetadata"})
    @ResponseBody
    public JsonResult updateMetadata(Long id, String name, String description, String labelAliasesZh) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        try {
            Object result = algorithmPackageLifecycleService.updateMetadata(id, name, description, labelAliasesZh);
            operationLogService.record("algorithm:metadata_update", "algorithmId=" + id, true, "algorithm metadata updated", name);
            return JsonResultUtils.success(result);
        } catch (Exception ex) {
            operationLogService.record("algorithm:metadata_update", "algorithmId=" + id, false, "algorithm metadata update failed", ex.getMessage());
            return JsonResultUtils.fail(ex.getMessage());
        }
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
