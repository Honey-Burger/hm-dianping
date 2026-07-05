package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryShopTypeByList() {
        String key = CACHE_SHOP_TYPE_KEY;
        //1.从Redis查询商铺类型缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            try {
                List<ShopType> shopTypeList = JSONUtil.toList(shopJson, ShopType.class);
                return Result.ok(shopTypeList);
            } catch (Exception e) {
                stringRedisTemplate.delete(key);
            }
        }
        //4.不存在，根据id查询数据库
        List<ShopType> shopTypeList = list();
        //5.数据库里不存在，返回错误
        if (shopTypeList.isEmpty()){
            return Result.fail("店铺类型不存在!");
        }
        //6.存在，写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList),30, TimeUnit.MINUTES);
        //7.返回
        return Result.ok(shopTypeList);
    }
}
