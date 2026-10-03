package com.faber.api.flow.manage.biz;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.faber.api.flow.form.biz.FlowFormBiz;
import com.faber.api.flow.form.entity.FlowForm;
import com.faber.api.flow.form.enums.FlowFormStatusEnum;
import com.faber.api.flow.form.enums.FlowFormTypeEnum;
import com.faber.api.flow.form.vo.config.FlowFormDataConfig;
import com.faber.api.flow.manage.entity.FlowProcess;
import com.faber.core.exception.BuzzException;

/** 独立运行，不启动 Spring、Redis 或数据库。 */
public class FlowProcessSharedFormTest {
    public static void main(String[] args) throws Exception {
        FlowForm form = new FlowForm();
        form.setId(10);
        form.setFlowProcessId(1);
        form.setStatus(FlowFormStatusEnum.ENABLED);
        form.setType(FlowFormTypeEnum.DESIGN);
        form.setConfig(Map.of());
        form.setTableName("ff_shared_form");
        FlowFormDataConfig.Column column = new FlowFormDataConfig.Column();
        column.setField("name");
        FlowFormDataConfig.Table main = new FlowFormDataConfig.Table();
        main.setTableName("ff_shared_form");
        main.setColumns(List.of(column));
        FlowFormDataConfig config = new FlowFormDataConfig();
        config.setMain(main);
        form.setDataConfig(config);

        FlowProcessBiz biz = new FlowProcessBiz();
        biz.flowFormBiz = new FlowFormBiz() {
            @Override
            public FlowForm getById(Serializable id) {
                return Integer.valueOf(10).equals(id) ? form : null;
            }
        };
        Method validate = FlowProcessBiz.class.getDeclaredMethod("validateCustomForm", FlowProcess.class);
        validate.setAccessible(true);
        FlowProcess process = new FlowProcess();
        process.setFormId(10);
        for (int id : List.of(1, 2)) {
            process.setId(id);
            check(validate.invoke(biz, process) == form, "两个流程均可复用旧绑定表单");
        }
        check(form.getFlowProcessId() == 1, "不覆盖旧绑定");
        form.setFlowProcessId(null);
        check(validate.invoke(biz, process) == form, "没有旧绑定也可关联");
        process.setFormId(null);
        expectFailure(validate, biz, process, "自定义流程表单 ID 不能为空");
        process.setFormId(99);
        expectFailure(validate, biz, process, "流程表单不存在");
        process.setFormId(10);
        form.setStatus(null);
        expectFailure(validate, biz, process, "流程表单未启用");
        form.setStatus(FlowFormStatusEnum.ENABLED);
        form.setType(null);
        expectFailure(validate, biz, process, "流程表单类型不支持");
        form.setType(FlowFormTypeEnum.DESIGN);
        form.setConfig(null);
        expectFailure(validate, biz, process, "流程表单配置不能为空");
        form.setConfig(Map.of());
        form.setDataConfig(null);
        expectFailure(validate, biz, process, "流程表单主表配置无效");
        form.setDataConfig(config);
        form.setTableName("ff_another_form");
        expectFailure(validate, biz, process, "流程表单表名与数据配置不一致");
        System.out.println("FlowProcessSharedFormTest passed");
    }

    private static void expectFailure(Method method, FlowProcessBiz biz, FlowProcess process, String message) throws Exception {
        try {
            method.invoke(biz, process);
            throw new AssertionError("应拒绝：" + message);
        } catch (InvocationTargetException e) {
            check(e.getCause() instanceof BuzzException && e.getCause().getMessage().contains(message), message);
        }
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
