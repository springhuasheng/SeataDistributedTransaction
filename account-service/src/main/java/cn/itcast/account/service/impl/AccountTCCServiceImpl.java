package cn.itcast.account.service.impl;

import cn.itcast.account.entity.AccountFreeze;
import cn.itcast.account.mapper.AccountFreezeMapper;
import cn.itcast.account.mapper.AccountMapper;
import cn.itcast.account.service.AccountTCCService;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountTCCServiceImpl implements AccountTCCService {

    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private AccountFreezeMapper freezeMapper;

    // Try方法
    @Override
    @Transactional
    public void deduct(String userId, int money) {
        // 0.获取事务id
        String xid = RootContext.getXID();
        // 1.扣减可用余额
        accountMapper.deduct(userId, money);

        // 先根据事务id去查询冻结表
        // 如果能查询到记录 说明要做业务悬挂
        AccountFreeze freeze2 = freezeMapper.selectById(xid);
        if (freeze2 != null) {
            // 说明这个事务之前已经回滚了
            return;
        }

        // 2.记录冻结金额，事务状态
        AccountFreeze freeze = new AccountFreeze();
        freeze.setUserId(userId);
        freeze.setFreezeMoney(money);
        freeze.setState(AccountFreeze.State.TRY);
        freeze.setXid(xid);
        freezeMapper.insert(freeze);
    }

    // 事务在第二阶段进行一个提交
    @Override
    public boolean confirm(BusinessActionContext ctx) {
        // 1.获取事务id
        String xid = ctx.getXid();
        // 2.根据id删除冻结记录
        int count = freezeMapper.deleteById(xid);
        return count == 1;
    }

    // 事务执行失败 回滚
    @Override
    public boolean cancel(BusinessActionContext ctx) {
        // 0.查询冻结记录
        String xid = ctx.getXid();
        AccountFreeze freeze = freezeMapper.selectById(xid);

        // 判断 如果事务之前没有try过 这边要进行空回滚
        // 如果这个对象是null 要做空回滚
        if (freeze == null) {
            freeze.setUserId(ctx.getActionContext("userId").toString());
            freeze.setFreezeMoney(0);
            freeze.setState(AccountFreeze.State.CANCEL);
            freeze.setXid(xid);
            freezeMapper.insert(freeze);
        }

        // 幂等性   Dubbo  集群容错  默认的配置  超时重试  默认重试2次  + 1 = 3次
        // 哪些方法能进行重试 哪些方法不能重试
        // 增      不是幂等性
        // 删      是幂等性
        // 改      是幂等性/不是幂等性  set age = age + 10;
        // 查      是幂等性

        if (freeze.getState() == AccountFreeze.State.CANCEL) {
            // 之前就已经回滚过  要保证幂等性 就不要再次回滚
            return true;
        }

        // 1.恢复可用余额
        accountMapper.refund(freeze.getUserId(), freeze.getFreezeMoney());
        // 2.将冻结金额清零，状态改为CANCEL
        freeze.setFreezeMoney(0);
        freeze.setState(AccountFreeze.State.CANCEL);
        int count = freezeMapper.updateById(freeze);
        return count == 1;
    }
}
