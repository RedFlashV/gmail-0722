package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.dao.SkuInfoDao;
import com.atguigu.gmall.pms.dao.SpuInfoDescDao;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.service.ProductAttrValueService;
import com.atguigu.gmall.pms.service.SkuImagesService;
import com.atguigu.gmall.pms.service.SkuSaleAttrValueService;
import com.atguigu.gmall.pms.vo.BaseAttrVO;
import com.atguigu.gmall.pms.vo.SkuInfoVo;
import com.atguigu.gmall.pms.vo.SpuInfoVO;
import com.atguigu.gmall.sms.vo.SkuSaleVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.pms.dao.SpuInfoDao;
import com.atguigu.gmall.pms.service.SpuInfoService;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {
    @Autowired
    private SpuInfoDescDao descDao;

    @Autowired
    private ProductAttrValueService attrValueService;

    @Autowired
    private SkuInfoDao skuInfoDao;

    @Autowired
    private SkuImagesService skuImagesService;
    @Autowired
    private SkuSaleAttrValueService saleAttrValueService;
    @Autowired
    private GmallSmsClient gmallSmsClient;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageVo(page);
    }

    @Override
    public PageVo querySpuPage(QueryCondition condition, Long cid) {
        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();
        //判断分类是否为0
        if (cid != null && cid != 0) {
            wrapper.eq("catalog_id", cid);
        }
        //判断关键字是否为空
        String key = condition.getKey();
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(t -> t.eq("id", key).or().like("spu_name", key));
        }

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(condition),
                wrapper
        );

        return new PageVo(page);
    }

    @Override
    public void bigsave(SpuInfoVO spuInfoVO) {
        //1.保存spu相关的三张表
        //1.1保存pms_spu_Info信息
        spuInfoVO.setCreateTime(new Date());
        spuInfoVO.setUodateTime(spuInfoVO.getCreateTime());
        this.save(spuInfoVO);
        Long spuId = spuInfoVO.getId();

        //1.2保存pms_spu_info_desc
        List<String> spuImages = spuInfoVO.getSpuImages();
        if (!CollectionUtils.isEmpty(spuImages)) {
            SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
            descEntity.setSpuId(spuId);
            descEntity.setDecript(StringUtils.join(spuImages, ","));
            this.descDao.insert(descEntity);
        }

        //1.3保存pms_product_attr_value
        List<BaseAttrVO> baseAttrs = spuInfoVO.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            List<ProductAttrValueEntity> attrValueEntities = baseAttrs.stream().map(baseAttrVO -> {
                ProductAttrValueEntity attrValueEntity = baseAttrVO;
                attrValueEntity.setSpuId(spuId);
                return attrValueEntity;
            }).collect(Collectors.toList());
            this.attrValueService.saveBatch(attrValueEntities);
        }


        //2.保存sku相关的三张表
        List<SkuInfoVo> skus = spuInfoVO.getSkus();
        if (CollectionUtils.isEmpty(skus)) {
            return;
        }
        skus.forEach(skuInfoVo -> {
            //2.1保存pms_sku_info
            skuInfoVo.setSpuId(spuId);
            skuInfoVo.setSkuCode(UUID.randomUUID().toString());
            skuInfoVo.setBrandId(spuInfoVO.getBrandId());
            skuInfoVo.setCatalogId(spuInfoVO.getCatalogId());
            List<String> images = skuInfoVo.getImages();
            //设置默认图片
            if (!CollectionUtils.isEmpty(images)) {
                skuInfoVo.setSkuDefaultImg(StringUtils.isNotBlank(skuInfoVo.getSkuDefaultImg()) ? skuInfoVo.getSkuDefaultImg() : images.get(0));
            }
            this.skuInfoDao.insert(skuInfoVo);
            Long skuId = skuInfoVo.getSkuId();
            //2.2保存pms_sku_images
            if (!CollectionUtils.isEmpty(images)) {
                List<SkuImagesEntity> skuImagesEntities = images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setImgUrl(image);
                    skuImagesEntity.setSkuId(skuId);
                    //设置是否默认图片
                    skuImagesEntity.setDefaultImg(StringUtils.equals(skuInfoVo.getSkuDefaultImg(), image) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList());
                this.skuImagesService.saveBatch(skuImagesEntities);
            }
            //2.3保存pms_sale_attr_value
            List<SkuSaleAttrValueEntity> saleAttrs = skuInfoVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)) {
                //设置skuId
                saleAttrs.forEach(skuSaleAttrValueEntity -> skuSaleAttrValueEntity.setSkuId(skuId));
                //批量保存销售属性
                this.saleAttrValueService.saveBatch(saleAttrs);
            }


            //3.保存营销信息的三张表(feign远程调用sms保存)
            SkuSaleVO skuSaleVO = new SkuSaleVO();
            BeanUtils.copyProperties(skuInfoVo, skuSaleVO);
            skuSaleVO.setSkuId(skuId);
            this.gmallSmsClient.saveSale(skuSaleVO);

        });
    }

}