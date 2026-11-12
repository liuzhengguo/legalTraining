package com.legaltraining.quartz.task;

import org.springframework.stereotype.Component;
import com.legaltraining.common.utils.StringUtils;

/**
 * 定时任务调度测试
 * 
 */
@Component("demoTask")
public class DemoTask
{
    public void demoMultipleParams(String s, Boolean b, Long l, Double d, Integer i)
    {
        System.out.println(StringUtils.format("执行多参方法： 字符串类型{}，布尔类型{}，长整型{}，浮点型{}，整形{}", s, b, l, d, i));
    }

    public void demoParams(String params)
    {
        System.out.println("执行有参方法：" + params);
    }

    public void demoNoParams()
    {
        System.out.println("执行无参方法");
    }
}
