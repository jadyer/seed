package com.jadyer.seed.test;

import org.apache.commons.io.FileUtils;
import org.beetl.core.Configuration;
import org.beetl.core.GroupTemplate;
import org.beetl.core.Template;
import org.beetl.core.resource.FileResourceLoader;
import org.beetl.core.resource.StringTemplateResourceLoader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by 玄玉<https://jadyer.cn/> on 2018/12/11 9:26.
 */
public class BeetlTest {
    @Test
    public void buildFromFile() throws IOException {
        /*
        <!DOCTYPE HTML>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>beetl demo</title>
        </head>
        <body>
            长篇架空武侠小说：${book}，真的忍心烂尾么？？
        </body>
        </html>
        */
        //使用模板文件位置实例化模板对象
        GroupTemplate groupTemplate = new GroupTemplate(new FileResourceLoader("C:/Users/Jadyer/Desktop/"), Configuration.defaultConfiguration());
        //设置模板所需的共享变量
        Map<String, Object> sharedVars = new HashMap<>();
        sharedVars.put("book", "英雄志");
        groupTemplate.setSharedVars(sharedVars);
        //模板解析成真实的HTML到文件（注意：模板文件source.html必须存在，dest.html不存在时beetl会自动创建并填充内容，dest.html存在时beetl会覆盖该文件）
        groupTemplate.getTemplate("source.html").renderTo(FileUtils.openOutputStream(new File("C:/Users/Jadyer/Desktop/dest.html")));
        //生成PDF文件
        //WkhtmltopdfUtil.convert(null, "C:/Users/Jadyer/Desktop/dest.html", "C:/Users/Jadyer/Desktop/dest.pdf");
    }


    @Test
    public void buildFromString() throws IOException {
        GroupTemplate groupTemplate = new GroupTemplate(new StringTemplateResourceLoader(), Configuration.defaultConfiguration());
        //设置模板字符串
        Template template = groupTemplate.getTemplate("<!DOCTYPE HTML>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>beetl demo</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "    长篇架空武侠小说：${book}，真的忍心烂尾么？？\n" +
                "</body>\n" +
                "</html>");
        //设置模板所需的共享变量
        Map<String, Object> sharedVars = new HashMap<>();
        sharedVars.put("book", "英雄志");
        template.binding(sharedVars);
        //template.binding("book", "野狗志");
        //解析模板字符串
        String data = template.render();
        System.out.println(data);
    }
}