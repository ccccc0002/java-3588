package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.WareHouse;
import com.yihecode.camera.ai.mapper.WareHouseMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 基地管理， 树形结构管理, 适配中化定制需求
 * 省 -> 市 -> 基地
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class WareHouseServiceImpl extends ServiceImpl<WareHouseMapper, WareHouse> implements WareHouseService {

    /**
     * 获取根节点
     *
     * @return
     */
    @Override
    public WareHouse getRoot() {
        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WareHouse::getParentIndexCode, "-1");
        return this.getOne(queryWrapper, false);
    }

    /**
     * 根据上级节点查询
     *
     * @param parentId
     * @return
     */
//    @Override
//    public List<WareHouse> listByParent(Long parentId) {
//        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.eq(WareHouse::getParentId, parentId);
//        queryWrapper.orderByAsc(WareHouse::getId);
//        List<WareHouse> wareHouseList = this.list(queryWrapper);
//        if(wareHouseList == null) {
//            return new ArrayList<>();
//        }
//        return wareHouseList;
//    }

    /**
     * 查询所有
     */
    @Override
    public List<WareHouse> listAll() {
        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WareHouse::getTreeType, 0);
        List<WareHouse> wareHouseList = this.list(queryWrapper);
        queryWrapper.orderByAsc(WareHouse::getTreeLevel);
        if(wareHouseList == null) {
            return new ArrayList<>();
        }
        return wareHouseList;
    }

    /**
     * 修改状态为失效
     */
    @Override
    public void updateStatusDisabled() {
        LambdaUpdateWrapper<WareHouse> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(WareHouse::getStatus, 1)
                .eq(WareHouse::getStatus, 0);
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * 更新数据
     *
     * @param wareHouse
     */
    @Override
    public void saveData(WareHouse wareHouse) {
        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WareHouse::getIndexCode, wareHouse.getIndexCode());

        //
        WareHouse wareHouseDb = this.getOne(queryWrapper);
        if(wareHouseDb == null) {
            this.save(wareHouse);
        } else {
            wareHouse.setId(wareHouseDb.getId());
            wareHouse.setRtspUrl(wareHouseDb.getRtspUrl());
            wareHouse.setPullStatus(wareHouseDb.getPullStatus());
            this.updateById(wareHouse);
        }
    }

    /**
     * 删除所有失效数据
     */
    @Override
    public void deleteDisabled() {
        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WareHouse::getStatus, 1);
        this.remove(queryWrapper);
    }

    /**
     * 根据tree_level查找
     *
     * @param treeLevel
     * @return
     */
    @Override
    public List<WareHouse> listByLevel(Integer treeLevel) {
        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WareHouse::getTreeLevel, treeLevel);
        queryWrapper.eq(WareHouse::getTreeType, 0); // 只查询目录

        //
        List<WareHouse> wareHouseList = this.list(queryWrapper);
        if(wareHouseList == null) {
            return new ArrayList<>();
        }
        return wareHouseList;
    }

    /**
     * 分页查询
     *
     * @param pageObj
     * @param parentIndexCode
     * @return
     */
    @Override
    public IPage<WareHouse> listPage(IPage<WareHouse> pageObj, String parentIndexCode) {
        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WareHouse::getParentIndexCode, parentIndexCode);
        return this.page(pageObj, queryWrapper);
    }

    /**
     * 根据indexCode查询
     *
     * @param indexCode
     * @return
     */
    @Override
    public WareHouse getByIndexCode(String indexCode) {
        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WareHouse::getIndexCode, indexCode);
        return this.getOne(queryWrapper);
    }

    /**
     * 将子节点状态改为禁用
     *
     * @param indexCode
     */
    @Override
    public void updateSubDisabled(String indexCode) {
        LambdaUpdateWrapper<WareHouse> updateWrapper = new LambdaUpdateWrapper();
        updateWrapper.set(WareHouse::getStatus, 1)
                .eq(WareHouse::getParentIndexCode, indexCode);
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * 删除子节点下面为禁用的数据
     *
     * @param indexCode
     */
    @Override
    public void deleteSubDisabled(String indexCode) {
        LambdaUpdateWrapper<WareHouse> updateWrapper = new LambdaUpdateWrapper();
        updateWrapper.eq(WareHouse::getStatus, 1)
                .eq(WareHouse::getParentIndexCode, indexCode);
        this.remove(updateWrapper);
    }

    /**
     * 更新RTSP地址
     *
     * @param wareHouse1
     */
    @Override
    public void updateRtsp(WareHouse wareHouse1) {
        LambdaUpdateWrapper<WareHouse> updateWrapper = new LambdaUpdateWrapper();
        updateWrapper.set(WareHouse::getPullStatus, wareHouse1.getPullStatus())
                .set(WareHouse::getPullTime, wareHouse1.getPullTime())
                .set(WareHouse::getRtspUrl, wareHouse1.getRtspUrl())
                .eq(WareHouse::getId, wareHouse1.getId());
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * 查询子节点
     *
     * @param indexCode
     * @return
     */
    @Override
    public List<WareHouse> listChildren(String indexCode) {
        LambdaQueryWrapper<WareHouse> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WareHouse::getParentIndexCode, indexCode);
        List<WareHouse> wareHouseList = this.list(queryWrapper);
        if(wareHouseList == null) {
            return new ArrayList<>();
        }
        return wareHouseList;
    }
}