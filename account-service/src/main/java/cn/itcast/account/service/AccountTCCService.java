package cn.itcast.account.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

// AccountTCCService 是TCC全局事务的处理接口
// 用来代替原来的AccountService 接口的

@LocalTCC
public interface AccountTCCService {

    // TwoPhaseBusinessAction 这个注解标记这个方法是TCC中的Try方法
    // BusinessActionContextParameter 这个注解是把形参数据保存到 BusinessActionContext 中
    @TwoPhaseBusinessAction(name = "deduct", commitMethod = "confirm", rollbackMethod = "cancel")
    void deduct(@BusinessActionContextParameter(paramName = "userId") String userId,
                @BusinessActionContextParameter(paramName = "money")int money);

    boolean confirm(BusinessActionContext ctx);

    boolean cancel(BusinessActionContext ctx);
}
