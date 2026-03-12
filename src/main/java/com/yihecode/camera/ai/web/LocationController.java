package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.yihecode.camera.ai.entity.Location;
import com.yihecode.camera.ai.service.LocationService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.TreeResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

/**
 * 摄像头区域节点<树形结构>管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Controller
@RequestMapping({"/location"})
public class LocationController {

    @Autowired
    private LocationService locationService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 新增节点
     * @param parentId
     * @param modelMap
     * @return
     */
    @GetMapping({"/form"})
    public String form(Long parentId, ModelMap modelMap) {
        modelMap.addAttribute("parentId", parentId);
        //
        Location location = locationService.getById(parentId);
        modelMap.addAttribute("parentName", location == null ? "全局节点" : location.getName());
        return "location/form";
    }

    /**
     * 编辑节点
     * @param id
     * @param modelMap
     * @return
     */
    @GetMapping({"/edit"})
    public String edit(Long id, ModelMap modelMap) {
        Location location = locationService.getById(id);
        if(location == null) {
            modelMap.addAttribute("parentId", "");
            modelMap.addAttribute("parentName", "");
        } else {
            modelMap.addAttribute("id", location.getId());
            modelMap.addAttribute("name", location.getName());
            modelMap.addAttribute("longitude", location.getLongitude());
            modelMap.addAttribute("latitude", location.getLatitude());
            //
            if(location.getParentId() == null) {
                modelMap.addAttribute("parentId", "");
                modelMap.addAttribute("parentName", "");
            } else if(location.getParentId() == 0) {
                modelMap.addAttribute("parentId", "0");
                modelMap.addAttribute("parentName", "全局节点");
            } else {
                Location parentLocation = locationService.getById(location.getParentId());
                modelMap.addAttribute("parentId", parentLocation.getId());
                modelMap.addAttribute("parentName", parentLocation.getName());
            }
        }
        return "location/form";
    }

    /**
     * 保存
     * @param location
     * @return
     */
    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(Location location) throws Exception {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        locationService.saveNode(location);
        operationLogService.record("location:save", "locationId=" + location.getId(), true, "location saved", location.getName());
        return JsonResultUtils.success();
    }

    /**
     *
     * @param id
     * @return
     */
    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        this.locationService.deleteNodes(id);
        operationLogService.record("location:delete", "locationId=" + id, true, "location deleted", "");
        return JsonResultUtils.success();
    }

    /**
     * tree
     * @return
     */
    @RequestMapping(value = "/listTree")
    @ResponseBody
    public List<TreeResult> listTree() {
        List<Location> locationList = locationService.listData();

        //
        List<TreeResult> treeResultList = new ArrayList<>();
        for(Location location : locationList) {
            TreeResult treeResult = new TreeResult();
            treeResult.setMeId(location.getId() + "");
            treeResult.setText(location.getName());
            treeResult.setParent(location.getParentId() + "");
            treeResult.setChildren(new ArrayList<>());
            treeResult.setIcon("layui-icon layui-icon-location");
            treeResultList.add(treeResult);
        }

        //
        List<TreeResult> trees = new ArrayList<>();
        for(TreeResult treeResult : treeResultList) {
            if(treeResult.getParent().equals("0")) {
                treeResult.setParent("#");
                trees.add(findChildren(treeResult, treeResultList));
            }
        }
        return trees;
    }

    // 查询子节点
    private TreeResult findChildren(TreeResult tree, List<TreeResult> treeList) {
        for(TreeResult node : treeList) {
            if(tree.getMeId().equals(node.getParent())) {
                if(tree.getChildren() == null) {
                    tree.setChildren(new ArrayList<>());
                }
                tree.getChildren().add(findChildren(node, treeList));
                if(tree.getChildren() != null && tree.getChildren().size() > 0) {
                    tree.setIcon("layui-icon layui-icon-home");
                }
            }
        }
        return tree;
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
