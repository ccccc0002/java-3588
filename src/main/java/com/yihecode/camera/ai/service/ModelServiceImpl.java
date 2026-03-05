package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.Model;
import com.yihecode.camera.ai.entity.ModelDepend;
import com.yihecode.camera.ai.exception.BizException;
import com.yihecode.camera.ai.mapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * 婵☆垪鈧磭鈧绮婚敍鍕€?
 */
@Service
public class ModelServiceImpl extends ServiceImpl<ModelMapper, Model> implements ModelService {

    //
    @Autowired
    private ModelDependService modelDependService;

    //
    @Value("${modelDir}")
    public String modelDir;

    /**
     * 闁哄秷顫夊畵涔穘nx md5闁稿﹤鍚嬮悡锛勬嫚?     *
     * @param md5
     * @return
     */
    @Override
    public Model getByOnnxMd5(String md5) {
        LambdaQueryWrapper<Model> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Model::getOnnxMd5, md5);
        return this.getOne(queryWrapper);
    }

    /**
     * 闁哄秷顫夊畵渚€寮崶锔筋偨闁告艾绉惰ⅷ闁哄被鍎撮?
     *
     * @param fileName
     * @return
     */
    @Override
    public Model getByOnnxName(String fileName) {
        LambdaQueryWrapper<Model> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Model::getOnnxName, fileName);
        return this.getOne(queryWrapper);
    }

    /**
     * 闁告帒妫濋妴澶愬蓟閵夘煈鍤?
     *
     * @param pageObj
     * @return
     */
    @Override
    public IPage<Model> listPage(IPage<Model> pageObj) {
        LambdaQueryWrapper<Model> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Model::getState, 0);
        queryWrapper.orderByDesc(Model::getCreatedAt);
        return this.page(pageObj, queryWrapper);
    }

    /**
     * 闁哄被鍎撮妤呭极閻楀牆绁﹂柛鎺擃殙閵?
     *
     * @return
     */
    @Override
    public List<Model> listData() {
        LambdaQueryWrapper<Model> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Model::getState, 0);
        queryWrapper.orderByAsc(Model::getCreatedAt);
        //
        List<Model> modelList = this.list(queryWrapper);
        if(modelList == null) {
            return new ArrayList<>();
        }
        return modelList;
    }

    /**
     * 闁哄秷顫夊畵浣肝熼垾宕団偓鐑藉触瀹ュ泦鐐哄蓟閵夘煈鍤勯柡浣峰嵆閸?
     *
     * @param name
     * @return
     */
    @Override
    public int getActiveCountByName(String name) {
        LambdaQueryWrapper<Model> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Model::getName, name);
        queryWrapper.eq(Model::getState, 0);
        return Math.toIntExact(this.count(queryWrapper));
    }

    /**
     * 闁哄秷顫夊畵浣肝熼垾宕団偓鐑藉触瀹ュ泦鐐哄蓟閵夘煈鍤勯柣妤€鐗婂﹢浼村极娴兼潙娅?
     *
     * @param name
     * @return
     */
    @Override
    public int getVersionCountByName(String name) {
        LambdaQueryWrapper<Model> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Model::getName, name);
        return Math.toIntExact(this.count(queryWrapper));
    }

    /**
     * 闁哄洤鐡ㄩ弻濠囨偋閸喐鎷遍柡浣峰嵆閸?
     *
     * @param name
     * @param newVersionCount
     */
    @Override
    public void updateVersionCount(String name, int newVersionCount) {
        LambdaUpdateWrapper<Model> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Model::getName, name);
        updateWrapper.set(Model::getVersionCount, newVersionCount);
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * 濞ｅ洦绻傞悺銊ノ熼垾宕団偓?
     *
     * @param model
     * @throws Exception
     */
    @Override
    public Map<String, Object> saveModel(Model model) throws Exception {
        //
        Map<String, Object> retMap = new HashMap<>();
        retMap.put("msgType", 20001);
        retMap.put("msgText", "operation succeeded");
        retMap.put("modelId", model.getId());

        //
        if(model.getModelIds() != null) {
            for(Long dependModelId : model.getModelIds()) {
                Model dependModel = this.getById(dependModelId);
                if(dependModel.getName().equals(model.getName())) {
                    throw new BizException("dependent model with same name is not allowed");
                }
            }
        }

        //
        if(model.getId() == null) {
            //
            int activeCount = this.getActiveCountByName(model.getName());

            //
            model.setVersionCount(activeCount > 0 ? 1 : 0); // default disabled when same-name model exists
            model.setState(0);
            model.setCreatedAt(new Date());
            model.setVersionCount(0);
            if(StrUtil.isNotBlank(model.getOnnxName())) {
                File onnxFile = new File(modelDir + model.getOnnxName());
                if(onnxFile.exists()) {
                    model.setOnnxSize(onnxFile.length());
                } else {
                    model.setOnnxSize(0l);
                }
            } else {
                model.setOnnxSize(0l);
            }
            this.save(model);
            //
            retMap.put("modelId", model.getId());

            //
            int newVersionCount = this.getVersionCountByName(model.getName());
            this.updateVersionCount(model.getName(), newVersionCount);

            //
            if(activeCount > 0) {
                retMap.put("msgType", 20002);
                retMap.put("msgText", "Operation succeeded, current model is disabled by default. Enable now?");
            }
        } else {
            Model modelDb = this.getById(model.getId());
            if(modelDb == null) {
                throw new BizException("model not found");
            }
            //
            if(!modelDb.getName().equals(model.getName())) {
                throw new BizException("model name cannot be changed");
            }
            //
            this.saveOrUpdate(model);
        }

        //
        Long modelId = model.getId();
        //
        modelDependService.removeByModel(modelId);
        //
        if(model.getModelIds() != null) {
            for(Long dependModelId : model.getModelIds()) {
                ModelDepend modelDepend = new ModelDepend();
                modelDepend.setModelId(modelId);
                modelDepend.setDependModelId(dependModelId);
                modelDependService.save(modelDepend);
            }
        }
        return retMap;
    }

    /**
     * 婵☆垪鈧磭鈧兘宕ラ婊勬殢
     *
     * @param modelId
     */
    @Override
    public void updateModelEnable(Long modelId) throws Exception {
        //
        Model model = this.getById(modelId);
        if(model == null) {
            throw new BizException("model not found");
        }
        //
        if(model.getState() == 0) {
            throw new BizException("model is already enabled");
        }
        //
        String modelName = model.getName();
        //
        LambdaUpdateWrapper<Model> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Model::getState, 1);
        updateWrapper.eq(Model::getName, modelName);
        updateWrapper.eq(Model::getState, 0);
        this.update(null, updateWrapper);

        //
        Model updateModel = new Model();
        updateModel.setId(modelId);
        updateModel.setState(0);
        this.updateById(updateModel);
    }

    /**
     * 闁哄被鍎撮妤€螣閳ュ磭鈧兘鎮ч崼鐔告嫳
     *
     * @param modelId
     * @return
     */
    @Override
    public List<Model> listVersion(Long modelId) {
        //
        Model model = this.getById(modelId);
        if(model == null) {
            return new ArrayList<>();
        }
        //
        String modelName = model.getName();
        //
        LambdaQueryWrapper<Model> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Model::getName, modelName);
        queryWrapper.orderByDesc(Model::getCreatedAt);
        List<Model> modelList = this.list(queryWrapper);
        if(modelList == null) {
            return new ArrayList<>();
        }
        return modelList;
    }
}
