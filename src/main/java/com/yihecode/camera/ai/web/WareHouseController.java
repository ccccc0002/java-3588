package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.WareHouse;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.service.WareHouseService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import com.yihecode.camera.ai.vo.TreeVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import cn.dev33.satoken.stp.StpUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * WareHouse tree management.
 */
@SaCheckLogin
@Controller
@RequestMapping({"/warehouse"})
public class WareHouseController {

    @Autowired
    private WareHouseService wareHouseService;

    @Autowired
    private CameraService cameraService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping({"", "/"})
    public String index(ModelMap modelMap) {
        int count = Math.toIntExact(wareHouseService.count());
        modelMap.addAttribute("count", count);
        return "warehouse/index";
    }

    @GetMapping({"/form"})
    public String form(Long parentId, ModelMap modelMap) {
        if(parentId == null || parentId == -1) {
            modelMap.addAttribute("parentId", "");
        } else {
            modelMap.addAttribute("parentId", parentId);
        }
        return "warehouse/form";
    }

    @GetMapping({"/form2"})
    public String form2(Long id, ModelMap modelMap) {
        WareHouse wareHouse = wareHouseService.getById(id);
        modelMap.addAttribute("warehouse", wareHouse);
        return "warehouse/form";
    }

    @PostMapping({"/listPage"})
    @ResponseBody
    public PageResult listPage(String parentIndexCode,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "10") Integer limit) {
        IPage<WareHouse> pageObj = new Page<>(page, limit);
        IPage<WareHouse> pageResult = wareHouseService.listPage(pageObj, parentIndexCode);
        return PageResultUtils.success(pageResult.getTotal(), pageResult.getRecords());
    }

    @RequestMapping(value = "/listTree")
    @ResponseBody
    public List<TreeVo> listTree() {
        List<WareHouse> wareHouseList = wareHouseService.listAll();

        List<TreeVo> treeVoList = new ArrayList<>();
        for(WareHouse wareHouse : wareHouseList) {
            TreeVo treeVo = new TreeVo();
            treeVo.setMeId(wareHouse.getIndexCode());
            treeVo.setText(wareHouse.getName());
            treeVo.setParent(wareHouse.getParentIndexCode());
            treeVo.setChildren(new ArrayList<>());
            if(wareHouse.getTreeType() == null || wareHouse.getTreeType() == 0) {
                treeVo.setIcon("layui-icon layui-icon-home");
            } else {
                treeVo.setIcon("layui-icon layui-icon-location");
            }
            treeVoList.add(treeVo);
        }

        List<TreeVo> trees = new ArrayList<>();
        for(TreeVo treeVo : treeVoList) {
            if("-1".equals(treeVo.getParent())) {
                treeVo.setParent("#");
                trees.add(findChildren(treeVo, treeVoList));
            }
        }
        return trees;
    }

    private TreeVo findChildren(TreeVo tree, List<TreeVo> treeList) {
        for(TreeVo node : treeList) {
            if(tree.getMeId().equals(node.getParent())) {
                if(tree.getChildren() == null) {
                    tree.setChildren(new ArrayList<>());
                }
                tree.getChildren().add(findChildren(node, treeList));
            }
        }
        return tree;
    }

    @PostMapping({"/sync2all"})
    @ResponseBody
    public JsonResult sync2all() {
        if (!roleAccessService.canSyncWarehouse(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        List<WareHouse> wareHouses = wareHouseService.list();
        if(wareHouses == null || wareHouses.isEmpty()) {
            return JsonResultUtils.fail("No data to sync");
        }

        List<WareHouse> cameraNodes = new ArrayList<>();
        for(WareHouse wareHouse : wareHouses) {
            if(wareHouse != null
                    && wareHouse.getTreeType() != null
                    && wareHouse.getTreeType() == 1
                    && StrUtil.isNotBlank(wareHouse.getRtspUrl())) {
                cameraNodes.add(wareHouse);
            }
        }

        if(cameraNodes.isEmpty()) {
            return JsonResultUtils.fail("No camera nodes to sync");
        }

        SyncResult syncResult = syncWarehousesToCameras(cameraNodes);
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("synced", syncResult.synced);
        dataMap.put("skipped_by_license", syncResult.skippedByLicense);
        operationLogService.record("warehouse:sync_all", "warehouse", true, "sync all finished", dataMap.toString());
        return JsonResultUtils.success(dataMap);
    }

    @PostMapping({"/sync2node"})
    @ResponseBody
    public JsonResult sync2node(String indexCode) {
        if (!roleAccessService.canSyncWarehouse(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        if(StrUtil.isBlank(indexCode)) {
            return JsonResultUtils.fail("No node selected");
        }

        WareHouse wareHouse = wareHouseService.getByIndexCode(indexCode);
        if(wareHouse == null) {
            return JsonResultUtils.fail("Node not found");
        }

        List<WareHouse> cameraNodes = new ArrayList<>();
        if(wareHouse.getTreeType() != null && wareHouse.getTreeType() == 1) {
            if(StrUtil.isNotBlank(wareHouse.getRtspUrl())) {
                cameraNodes.add(wareHouse);
            }
        } else {
            cameraNodes.addAll(listCameraNodesByRoot(indexCode));
        }

        if(cameraNodes.isEmpty()) {
            return JsonResultUtils.fail("No camera nodes under current node");
        }

        SyncResult syncResult = syncWarehousesToCameras(cameraNodes);
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("synced", syncResult.synced);
        dataMap.put("skipped_by_license", syncResult.skippedByLicense);
        operationLogService.record("warehouse:sync_node", "indexCode=" + indexCode, true, "sync node finished", dataMap.toString());
        return JsonResultUtils.success(dataMap);
    }

    @PostMapping({"/pullRtsp"})
    @ResponseBody
    public JsonResult pullRtsp(String indexCode) {
        if (!roleAccessService.canSyncWarehouse(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        if(StrUtil.isBlank(indexCode)) {
            return JsonResultUtils.fail("No node selected");
        }

        WareHouse wareHouse = wareHouseService.getByIndexCode(indexCode);
        if(wareHouse == null) {
            return JsonResultUtils.fail("Node not found");
        }

        List<WareHouse> cameraNodes = new ArrayList<>();
        if(wareHouse.getTreeType() != null && wareHouse.getTreeType() == 1) {
            cameraNodes.add(wareHouse);
        } else {
            cameraNodes.addAll(listCameraNodesByRoot(indexCode));
        }

        if(cameraNodes.isEmpty()) {
            return JsonResultUtils.fail("No camera nodes under current node");
        }

        int okCount = 0;
        int failCount = 0;
        Date now = new Date();
        for(WareHouse node : cameraNodes) {
            WareHouse update = new WareHouse();
            update.setId(node.getId());
            update.setPullTime(now);
            if(StrUtil.isBlank(node.getRtspUrl())) {
                update.setPullStatus(1);
                failCount++;
            } else {
                update.setPullStatus(0);
                okCount++;
            }
            wareHouseService.updateById(update);
        }

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("total", cameraNodes.size());
        dataMap.put("ok", okCount);
        dataMap.put("fail", failCount);
        operationLogService.record("warehouse:pull_rtsp", "indexCode=" + indexCode, true, "pull rtsp finished", dataMap.toString());
        return JsonResultUtils.success(dataMap);
    }

    @PostMapping({"/select2export"})
    @ResponseBody
    public JsonResult select2export(String ids) {
        if (!roleAccessService.canSyncWarehouse(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        if(StrUtil.isBlank(ids)) {
            return JsonResultUtils.fail("Please select data first");
        }

        String[] idArr = ids.split(",");
        List<WareHouse> selectedNodes = new ArrayList<>();
        for(String idStr : idArr) {
            if(StrUtil.isBlank(idStr)) {
                continue;
            }
            WareHouse wareHouse = wareHouseService.getById(Long.parseLong(idStr));
            if(wareHouse == null) {
                continue;
            }
            if(wareHouse.getTreeType() != null && wareHouse.getTreeType() == 1) {
                selectedNodes.add(wareHouse);
            } else {
                selectedNodes.addAll(listCameraNodesByRoot(wareHouse.getIndexCode()));
            }
        }

        if(selectedNodes.isEmpty()) {
            return JsonResultUtils.fail("No camera nodes to import");
        }

        SyncResult syncResult = syncWarehousesToCameras(selectedNodes);
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("synced", syncResult.synced);
        dataMap.put("skipped_by_license", syncResult.skippedByLicense);
        operationLogService.record("warehouse:select_export", "ids=" + ids, true, "select export finished", dataMap.toString());
        return JsonResultUtils.success(dataMap);
    }

    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(WareHouse wareHouse,
                           @RequestParam(value = "title", required = false) String title,
                           @RequestParam(value = "parentId", required = false) Long parentId) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("warehouse:save", "warehouse", false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        if(wareHouse == null) {
            wareHouse = new WareHouse();
        }

        String name = StrUtil.isNotBlank(wareHouse.getName()) ? wareHouse.getName() : title;
        if(StrUtil.isBlank(name)) {
            return JsonResultUtils.fail("Node name is required");
        }
        wareHouse.setName(name.trim());

        if(wareHouse.getId() == null) {
            if(StrUtil.isBlank(wareHouse.getIndexCode())) {
                wareHouse.setIndexCode("W" + UUID.randomUUID().toString().replace("-", ""));
            }

            if(StrUtil.isBlank(wareHouse.getParentIndexCode())) {
                if(parentId != null && parentId > 0) {
                    WareHouse parent = wareHouseService.getById(parentId);
                    if(parent == null || StrUtil.isBlank(parent.getIndexCode())) {
                        return JsonResultUtils.fail("Parent node not found");
                    }
                    wareHouse.setParentIndexCode(parent.getIndexCode());
                    if(wareHouse.getTreeLevel() == null || wareHouse.getTreeLevel() <= 0) {
                        int parentLevel = parent.getTreeLevel() == null ? 0 : parent.getTreeLevel();
                        wareHouse.setTreeLevel(parentLevel + 1);
                    }
                } else {
                    wareHouse.setParentIndexCode("-1");
                    if(wareHouse.getTreeLevel() == null || wareHouse.getTreeLevel() <= 0) {
                        wareHouse.setTreeLevel(1);
                    }
                }
            } else if(wareHouse.getTreeLevel() == null || wareHouse.getTreeLevel() <= 0) {
                WareHouse parent = wareHouseService.getByIndexCode(wareHouse.getParentIndexCode());
                int parentLevel = parent == null || parent.getTreeLevel() == null ? 0 : parent.getTreeLevel();
                wareHouse.setTreeLevel(parentLevel + 1);
            }
        }

        if(wareHouse.getStatus() == null) {
            wareHouse.setStatus(0);
        }
        if(wareHouse.getTreeType() == null) {
            wareHouse.setTreeType(0);
        }

        wareHouseService.saveOrUpdate(wareHouse);
        operationLogService.record("warehouse:save", "warehouseId=" + wareHouse.getId(), true, "warehouse saved", wareHouse.getName());
        return JsonResultUtils.success();
    }

    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("warehouse:delete", "warehouseId=" + id, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        if(id == null) {
            return JsonResultUtils.fail("Invalid parameter");
        }

        WareHouse wareHouse = wareHouseService.getById(id);
        if(wareHouse == null) {
            return JsonResultUtils.fail("Node not found");
        }

        List<WareHouse> subTree = new ArrayList<>();
        collectSubTree(wareHouse.getIndexCode(), subTree, new HashSet<>());
        if(subTree.isEmpty()) {
            subTree.add(wareHouse);
        }

        for(WareHouse node : subTree) {
            if(node == null || node.getId() == null) {
                continue;
            }
            Camera camera = cameraService.getByWareHouseId(node.getId());
            if(camera != null && camera.getId() != null) {
                cameraService.delete(camera.getId());
            }
        }

        int deleted = 0;
        for(int i = subTree.size() - 1; i >= 0; i--) {
            WareHouse node = subTree.get(i);
            if(node == null || node.getId() == null) {
                continue;
            }
            if(wareHouseService.removeById(node.getId())) {
                deleted++;
            }
        }

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("deleted", deleted);
        operationLogService.record("warehouse:delete", "warehouseId=" + id, true, "warehouse deleted", "deleted=" + deleted);
        return JsonResultUtils.success(dataMap);
    }

    private List<WareHouse> listCameraNodesByRoot(String indexCode) {
        List<WareHouse> result = new ArrayList<>();
        collectCameraNodes(indexCode, result, new HashSet<>());
        return result;
    }

    private void collectCameraNodes(String indexCode, List<WareHouse> result, Set<String> visited) {
        if(StrUtil.isBlank(indexCode) || visited.contains(indexCode)) {
            return;
        }
        visited.add(indexCode);

        WareHouse current = wareHouseService.getByIndexCode(indexCode);
        if(current == null) {
            return;
        }
        if(current.getTreeType() != null && current.getTreeType() == 1) {
            result.add(current);
            return;
        }

        List<WareHouse> children = wareHouseService.listChildren(indexCode);
        if(children == null || children.isEmpty()) {
            return;
        }
        for(WareHouse child : children) {
            if(child == null || StrUtil.isBlank(child.getIndexCode())) {
                continue;
            }
            collectCameraNodes(child.getIndexCode(), result, visited);
        }
    }

    private SyncResult syncWarehousesToCameras(List<WareHouse> wareHouses) {
        SyncResult result = new SyncResult();
        int maxChannels = parseLicenseMaxChannels();
        int currentCameraCount = getCurrentCameraCount();
        for(WareHouse wareHouse : wareHouses) {
            if(wareHouse == null
                    || wareHouse.getId() == null
                    || StrUtil.isBlank(wareHouse.getRtspUrl())
                    || wareHouse.getTreeType() == null
                    || wareHouse.getTreeType() != 1) {
                continue;
            }

            Camera camera = cameraService.getByWareHouseId(wareHouse.getId());
            if(camera == null) {
                if(maxChannels > 0 && currentCameraCount >= maxChannels) {
                    result.skippedByLicense++;
                    continue;
                }
                cameraService.save(buildNewCamera(wareHouse));
                currentCameraCount++;
                result.synced++;
                continue;
            }

            Camera update = new Camera();
            update.setId(camera.getId());
            boolean changed = false;
            if(!Objects.equals(camera.getName(), wareHouse.getName())) {
                update.setName(wareHouse.getName());
                changed = true;
            }
            if(!Objects.equals(camera.getRtspUrl(), wareHouse.getRtspUrl())) {
                update.setRtspUrl(wareHouse.getRtspUrl());
                changed = true;
            }
            if(changed) {
                update.setUpdatedAt(new Date());
                cameraService.updateById(update);
            }
            result.synced++;
        }
        return result;
    }

    private int parseLicenseMaxChannels() {
        String raw = configService.getByValTag("license_max_channels");
        if(StrUtil.isBlank(raw)) {
            return 0;
        }
        try {
            int val = Integer.parseInt(raw.trim());
            return Math.max(val, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private int getCurrentCameraCount() {
        List<Camera> cameras = cameraService.listData();
        return cameras == null ? 0 : cameras.size();
    }

    private static class SyncResult {
        private int synced;
        private int skippedByLicense;
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private Camera buildNewCamera(WareHouse wareHouse) {
        Camera newCamera = new Camera();
        newCamera.setName(wareHouse.getName());
        newCamera.setRtspUrl(wareHouse.getRtspUrl());
        newCamera.setAction(0);
        newCamera.setRunning(0);
        newCamera.setState(0);
        newCamera.setCreatedAt(new Date());
        newCamera.setUpdatedAt(new Date());
        newCamera.setFrequency(1000);
        newCamera.setIntervalTime(10f);
        newCamera.setFileWidth(0);
        newCamera.setFileHeight(0);
        newCamera.setCanvasWidth(0);
        newCamera.setCanvasHeight(0);
        newCamera.setScaleRatio(0f);
        newCamera.setWareHouseId(wareHouse.getId());
        return newCamera;
    }

    private void collectSubTree(String indexCode, List<WareHouse> result, Set<String> visited) {
        if(StrUtil.isBlank(indexCode) || visited.contains(indexCode)) {
            return;
        }
        visited.add(indexCode);

        WareHouse current = wareHouseService.getByIndexCode(indexCode);
        if(current == null) {
            return;
        }
        result.add(current);

        List<WareHouse> children = wareHouseService.listChildren(indexCode);
        if(children == null || children.isEmpty()) {
            return;
        }
        for(WareHouse child : children) {
            if(child == null || StrUtil.isBlank(child.getIndexCode())) {
                continue;
            }
            collectSubTree(child.getIndexCode(), result, visited);
        }
    }
}
