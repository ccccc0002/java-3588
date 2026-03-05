package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.WareHouse;

import java.util.List;

/**
 * 仓库管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface WareHouseService extends IService<WareHouse> {

    /**
     * 获取根节点
     * @return
     */
    WareHouse getRoot();

    /**
     * 根据上级节点查询
     * @param parentId
     * @return
     */
//    List<WareHouse> listByParent(Long parentId);

    /**
     * 查询所有
     */
    List<WareHouse> listAll();

    /**
     * 修改状态为失效
     */
    void updateStatusDisabled();

    /**
     * 更新数据
     * @param wareHouse
     */
    void saveData(WareHouse wareHouse);

    /**
     * 删除所有失效数据
     */
    void deleteDisabled();

    /**
     * 根据tree_level查找
     * @param treeLevel
     * @return
     */
    List<WareHouse> listByLevel(Integer treeLevel);

    /**
     * 分页查询
     * @param pageObj
     * @param parentIndexCode
     * @return
     */
    IPage<WareHouse> listPage(IPage<WareHouse> pageObj, String parentIndexCode);

    /**
     * 根据indexCode查询
     * @param indexCode
     * @return
     */
    WareHouse getByIndexCode(String indexCode);

    /**
     * 将子节点状态改为禁用
     * @param indexCode
     */
    void updateSubDisabled(String indexCode);

    /**
     * 删除子节点下面为禁用的数据
     * @param indexCode
     */
    void deleteSubDisabled(String indexCode);

    /**
     * 更新RTSP地址
     * @param wareHouse1
     */
    void updateRtsp(WareHouse wareHouse1);

    /**
     * 查询子节点
     * @param indexCode
     * @return
     */
    List<WareHouse> listChildren(String indexCode);
}