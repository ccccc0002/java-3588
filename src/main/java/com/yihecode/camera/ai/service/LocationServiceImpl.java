package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.Location;
import com.yihecode.camera.ai.exception.BizException;
import com.yihecode.camera.ai.mapper.LocationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 摄像头区域节点<树形结构>表
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class LocationServiceImpl extends ServiceImpl<LocationMapper, Location> implements LocationService {

    //
    @Autowired
    private CameraService cameraService;

    /**
     * 新增区域
     *
     * @param location
     */
    @Override
    public void saveNode(Location location) throws BizException {
        // 判断节点名称
        if(StrUtil.isBlank(location.getName())) {
            throw new BizException("区域名称不能为空");
        }

        //
        if(location.getId() == null) {
            //
            if(location.getParentId() == null) {
                throw new BizException("请选择上级区域节点");
            }
            //
            String parentNames = "";
            String parentIds = "";
            if(location.getParentId() > 0) {
                // 查询上级节点
                Location parentLocation = this.getById(location.getParentId());
                if(parentLocation == null) {
                    throw new BizException("上级区域节点不存在");
                }
                parentNames = (StrUtil.isBlank(parentLocation.getParentNames()) ? "" : (parentLocation.getParentNames() + "/")) + parentLocation.getName();
                parentIds = parentLocation.getParentIds() + "/" + parentLocation.getId();
            }
            location.setParentNames(parentNames);
            location.setParentIds(parentIds);
            //
            this.save(location);
        } else {
            // 查询历史数据
            Location locationDb = this.getById(location.getId());
            if(locationDb == null) {
                throw new BizException("区域节点不存在");
            }

            // 更新节点
            this.saveOrUpdate(location);

            // 更新下级节点名称
            if(!locationDb.getName().equals(location.getName())) {
                updateParentNames(location.getId());
            }
        }
    }

    /**
     * 更新区域子节点上级区域名称
     * @param parentId
     */
    private void updateParentNames(Long parentId) {
        //
        Location parentLocation = this.getById(parentId);
        if(parentLocation == null) {
            return ;
        }
        //
        List<Location> subLocationList = listByParent(parentId);
        for(Location subLocation : subLocationList) {
            //
            subLocation.setParentNames((StrUtil.isBlank(parentLocation.getParentNames()) ? "" : (parentLocation.getParentNames() + "/")) + parentLocation.getName());
            //
            this.saveOrUpdate(subLocation);
            //
            updateParentNames(subLocation.getId());
        }
    }

    /**
     * 根据上级节点查询
     * @param parentId
     * @return
     */
    private List<Location> listByParent(Long parentId) {
        LambdaQueryWrapper<Location> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Location::getParentId, parentId);
        List<Location> locations = this.list(queryWrapper);
        if(locations == null) {
            return new ArrayList<>();
        }
        return locations;
    }

    /**
     * 删除区域
     *
     * @param id
     */
    @Override
    public void deleteNodes(Long id) {
        deleteSubNodes(id);
    }

    /**
     * 删除所有子节点
     * @param parentId
     */
    private void deleteSubNodes(Long parentId) {
        //
        List<Location> subLocationList = listByParent(parentId);
        for(Location subLocation : subLocationList) {
            //
            deleteSubNodes(subLocation.getId());
        }
        //
        this.removeById(parentId);
        //
        cameraService.removeByLocation(parentId);
    }

    /**
     * 查询数据
     *
     * @return
     */
    @Override
    public List<Location> listData() {
        LambdaQueryWrapper<Location> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Location::getParentId);
        List<Location> locations = this.list(queryWrapper);
        if(locations == null) {
            return new ArrayList<>();
        }
        return locations;
    }
}