package com.jadyer.seed.controller.batch.javaconfig;

import com.alibaba.druid.pool.DruidDataSource;
import com.jadyer.seed.comm.exception.SeedException;
import com.jadyer.seed.comm.util.LogUtil;
import com.jadyer.seed.controller.batch.SettleJobListeners;
import com.jadyer.seed.controller.batch.model.Person;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 并行Step测试使用
 * Created by 玄玉<https://jadyer.cn/> on 2018/9/5 10:59.
 */
@Component
public class StepService0004 {
    @Resource
    private DruidDataSource dataSource;
    @Resource
    private StepBuilderFactory stepBuilderFactory;
    @Resource
    private SettleJobListeners settleJobListeners;

    @Bean
    public Step step0004(){
        return stepBuilderFactory.get("step0004")
                .listener(this.settleJobListeners)
                .<Person, Person>chunk(1)
                .reader(this.reader())
                .processor(this.processor())
                .writer(this.writer())
                .build();
    }


    private FlatFileItemReader<Person> reader(){
        return new FlatFileItemReaderBuilder<Person>()
                .name("step0001Reader")
                .resource(new FileSystemResource("/data/seedboot-batch.txt"))
                .strict(true)
                .encoding(StandardCharsets.UTF_8.displayName())
                .linesToSkip(1)
                .delimited().delimiter("|")
                .names(new String[]{"realName", "age", "birthDay"})
                .targetType(Person.class).customEditors(SettleJobConfiguration.customEditors)
                .build();
    }


    private ItemWriter<Person> writer(){
        return new ItemWriter<Person>() {
            @Override
            public void write(List<? extends Person> items) {
                //nothing to do
            }
        };
    }


    private ItemProcessor<Person, Person> processor(){
        return new ItemProcessor<Person, Person>() {
            @Override
            public Person process(Person item) {
                LogUtil.getLogger().info("读取到-->{}", ReflectionToStringBuilder.toString(item));
                if(item.getRealName().contains("玄玉")){
                    throw new SeedException("这是测试断点续跑的异常");
                }
                try {
                    TimeUnit.SECONDS.sleep(4);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return item;
            }
        };
    }
}