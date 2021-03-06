package com.jadyer.seed.controller.async;

import com.jadyer.seed.comm.constant.CommResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created by 玄玉<https://jadyer.cn/> on 2017/6/7 11:08.
 */
@RestController
@RequestMapping("/async")
public class AsyncDemoController {
    @Resource
    private AsyncDemoTask asyncDemoTask;

    @GetMapping("/do")
    public CommResult<String> doTask(){
        long start = System.currentTimeMillis();
        this.asyncDemoTask.doTaskOne();
        this.asyncDemoTask.doTaskTwo();
        this.asyncDemoTask.doTaskThree();
        return CommResult.success("所有任务已执行，耗时：" + (System.currentTimeMillis() - start) + "ms");
    }

    @GetMapping("/do/result")
    public CommResult<String> doTaskAndGetResult(){
        long start = System.currentTimeMillis();
        Future<String> task11 = this.asyncDemoTask.doTaskAndGetResultOne();
        Future<Integer> task22 = this.asyncDemoTask.doTaskAndGetResultTwo();
        Future<Long> task33 = this.asyncDemoTask.doTaskAndGetResultThree();
        while (!task11.isDone() || !task22.isDone() || !task33.isDone()) {
            try {
                TimeUnit.MILLISECONDS.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return CommResult.success("所有任务执行完毕，耗时：" + (System.currentTimeMillis() - start) + "ms");
    }
}