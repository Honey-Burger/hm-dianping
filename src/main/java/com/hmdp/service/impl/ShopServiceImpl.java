package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }


    public Shop queryWithMutex(Long id){//缓存击穿的实现逻辑
        String key = CACHE_SHOP_KEY + id;
        //1.从Redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //判断命中是否为空值
        if (shopJson != null){//走到这里时，shopJson 只可能是 "" 或 null
            return null;
        }

        //4.实现缓存重建
        //4.1 实现互斥锁
        String lockKey = "lock:shop:" + id;//定义锁的key
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2 判断是否获取成功
            if (!isLock){
                //4.3 失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
                //这里做了个递归，每次重试都从头走一遍逻辑（查缓存 → 抢锁），如果中途缓存已经重建好了，就提前命中缓存返回，不用非得自己抢到锁

            }

            //4.4 成功，根据id查询数据库
            shop = getById(id);
            //模拟重建的延时,用来测试互斥锁有没有产生作用
            Thread.sleep(200);
            //5.数据库里不存在，返回错误
            if (shop == null){
                //将空值写入Redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.存在，写入Redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL , TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unlock(lockKey);//不论线程有没有出现异常，只要持有锁到最后都要放锁
        }

        //8.返回
        return shop;
    }

    public Shop queryWithPassThrough(Long id){//防止缓存穿透的Redis查询
        String key = CACHE_SHOP_KEY + id;
        //1.从Redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中是否为空值
        if (shopJson != null){//走到这里时，shopJson 只可能是 "" 或 null
            return null;
        }
        //4.不存在，根据id查询数据库
        Shop shop = getById(id);
        //5.数据库里不存在
        if (shop == null){
            //将空值写入Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL , TimeUnit.MINUTES);
        //7.返回
        return shop;
    }

    private boolean tryLock(String key){//上互斥锁锁.这里的key是锁的key
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);//相当于SETNX
        return BooleanUtil.isTrue(aBoolean);//这里之所以不直接返回，因为Boolean对象可能为空指针
    }

    private void unlock(String key){//释放互斥锁
        stringRedisTemplate.delete(key);

    }


    @Override
    @Transactional//给方法加事务：要么全部成功，要么全部失败，保证数据不出错。任何一步报错 → 全部回滚，像没执行过一样
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return null;
    }
}