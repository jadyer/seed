package com.jadyer.seed.simcoder.helper;

import com.jadyer.seed.simcoder.SimcoderRun;
import com.jadyer.seed.simcoder.model.Column;
import com.jadyer.seed.simcoder.model.Table;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.beetl.core.Configuration;
import org.beetl.core.GroupTemplate;
import org.beetl.core.resource.ClasspathResourceLoader;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by 玄玉<https://jadyer.cn/> on 2017/9/8 23:22.
 */
public class GeneratorHelper {
    private static GroupTemplate groupTemplate = null;
    static {
        try {
            groupTemplate = new GroupTemplate(new ClasspathResourceLoader("templates/"), Configuration.defaultConfiguration());
            groupTemplate.setSharedVars(new HashMap<String, Object>(){
                private static final long serialVersionUID = -7774932094711543319L;
                {
                    if(SimcoderRun.IS_GENERATE_FEIGN_API){
                        put("IMPORT_COMMRESULT",         SimcoderRun.IMPORT_FEIGN_API_COMMRESULT);
                        put("IMPORT_CONSTANTS",          SimcoderRun.IMPORT_FEIGN_API_CONSTANTS);
                        put("IMPORT_PAGER",              SimcoderRun.IMPORT_FEIGN_API_PAGER);
                        put("IMPORT_PAGEUTIL",           SimcoderRun.IMPORT_FEIGN_API_PAGEUTIL);
                    }else{
                        put("IMPORT_COMMRESULT",         SimcoderRun.IMPORT_COMMRESULT);
                        put("IMPORT_CONSTANTS",          SimcoderRun.IMPORT_CONSTANTS);
                    }
                    put("IS_GENERATE_BUILDER",       SimcoderRun.IS_GENERATE_BUILDER);
                    put("IMPORT_DISABLEFORMVALID",    SimcoderRun.IMPORT_DISABLEFORMVALID);
                    put("IMPORT_ENABLEFORMVALID",    SimcoderRun.IMPORT_ENABLEFORMVALID);
                    put("IMPORT_ENABLELOG",          SimcoderRun.IMPORT_ENABLELOG);
                    put("IMPORT_JPA_BASEENTITY",     SimcoderRun.IMPORT_JPA_BASEENTITY);
                    put("IMPORT_JPA_BASEREPOSITORY", SimcoderRun.IMPORT_JPA_BASEREPOSITORY);
                    put("IMPORT_JPA_CONDITION",      SimcoderRun.IMPORT_JPA_CONDITION);
                    put("IMPORT_BEANUTIL",           SimcoderRun.IMPORT_BEANUTIL);
                    put("PACKAGE_API",               SimcoderRun.PACKAGE_API);
                    put("PACKAGE_DTO",               SimcoderRun.PACKAGE_DTO);
                    put("PACKAGE_REPOSITORY",        SimcoderRun.PACKAGE_REPOSITORY);
                    put("PACKAGE_MODEL",             SimcoderRun.PACKAGE_MODEL);
                    put("PACKAGE_SERVICE",           SimcoderRun.PACKAGE_SERVICE);
                    put("PACKAGE_CONTROLLER",        SimcoderRun.PACKAGE_CONTROLLER);
                }
            });
        } catch (IOException e) {
            System.err.println("加载Beetl模板失败，堆栈轨迹如下：");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 判断给定的表名是否存在于包含列表
     */
    private static boolean isInclude(String tablename, String... includeTablename){
        if(null==includeTablename || includeTablename.length==0){
            return true;
        }
        for(String obj : includeTablename){
            if(obj.equals(tablename)){
                return true;
            }
        }
        return false;
    }


    /**
     * 生成整个数据库的
     * @param databaseName     数据库名
     * @param includeTablename 包含列表，有此值时则以此值为准，即此时只为数据库中的这些表生成代码
     */
    public static void generate(String databaseName, String... includeTablename){
        List<Table> tableList = DBHelper.getTableList(databaseName);
        for(Table table : tableList){
            if(isInclude(table.getName(), includeTablename)){
                generateFromTable(table.getName(), table.getComment());
            }
        }
    }


    /**
     * 生成整个数据库的
     * @param databaseName    数据库名
     * @param tablenamePrefix 表名前缀，即此时只为数据库中的表名前缀为tablenamePrefix的表生成代码
     */
    public static void generate(String databaseName, String tablenamePrefix){
        List<Table> tableList = DBHelper.getTableList(databaseName);
        for(Table table : tableList){
            if(table.getName().startsWith(tablenamePrefix)){
                generateFromTable(table.getName(), table.getComment());
            }
        }
    }


    /**
     * 生成某张表的
     */
    private static void generateFromTable(String tablename, String tablecomment){
        boolean hasDate = false;
        boolean hasBigDecimal = false;
        boolean hasColumnAnnotation = false;
        boolean hasNotNullAnnotation = false;
        boolean hasNotBlankAnnotation = false;
        boolean hasNotBlankSizeAnnotation = false;
        StringBuilder fields = new StringBuilder();
        StringBuilder fields_dto = new StringBuilder();
        StringBuilder fields_toString = new StringBuilder();
        StringBuilder fields_BuilderSetValues = new StringBuilder();
        StringBuilder fields_BuilderNoAnnotations = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        StringBuilder methods_Builders = new StringBuilder();
        Map<String, String> fieldnameMap = new HashMap<>();
        List<Column> columnList = DBHelper.getColumnList(tablename);
        for(int i=0; i<columnList.size(); i++){
            Column column = columnList.get(i);
            if(StringUtils.equalsAnyIgnoreCase(column.getName(), "id", "create_time", "update_time")){
                continue;
            }
            // /* 属性注释 */
            if(StringUtils.isNotBlank(column.getComment())){
                fields.append("    /* ").append(column.getComment()).append(" */").append("\n");
            }
            // 暂时只对Integer、Long、Date、BigDecimal、String类型增加校验注解：@NotNull @NotBlank @Size(max=16)
            String javaType = DBHelper.buildJavatypeFromDbtype(column.getType());
            if(!column.isNullable() && !SimcoderRun.IS_GENERATE_FEIGN_API){
                if("Integer".equals(javaType) || "Long".equals(javaType) || "Date".equals(javaType) || "BigDecimal".equals(javaType)){
                    hasNotNullAnnotation = true;
                    fields.append("    @NotNull").append("\n");
                }
                if("String".equals(javaType)){
                    hasNotBlankAnnotation = true;
                    fields.append("    @NotBlank").append("\n");
                    if(column.getLength() > 0){
                        hasNotBlankSizeAnnotation = true;
                        fields.append("    @Size(");
                        /* 对于CHAR(6)类型的数据库字段，增加最小长度注解配置 */
                        if(column.getType().equals("char")){
                            fields.append("min=").append(column.getLength()).append(", ");
                        }
                        fields.append("max=").append(column.getLength()).append(")").append("\n");
                    }
                }
            }
            if(!column.isNullable() && SimcoderRun.IS_GENERATE_FEIGN_API){
                if("Integer".equals(javaType) || "Long".equals(javaType) || "Date".equals(javaType) || "BigDecimal".equals(javaType)){
                    hasNotNullAnnotation = true;
                    fields_dto.append("    @NotNull").append("\n");
                }
                if("String".equals(javaType)){
                    hasNotBlankAnnotation = true;
                    fields_dto.append("    @NotBlank").append("\n");
                    if(column.getLength() > 0){
                        hasNotBlankSizeAnnotation = true;
                        fields_dto.append("    @Size(");
                        /* 对于CHAR(6)类型的数据库字段，增加最小长度注解配置 */
                        if(column.getType().equals("char")){
                            fields_dto.append("min=").append(column.getLength()).append(", ");
                        }
                        fields_dto.append("max=").append(column.getLength()).append(")").append("\n");
                    }
                }
            }
            // @ApiModelProperty(value="定时任务的应用名称", required=true)
            if(StringUtils.isNotBlank(column.getComment())){
                if(!column.isNullable()){
                    fields.append("    @ApiModelProperty(value=\"").append(column.getComment()).append("\", required=true)").append("\n");
                    fields_dto.append("    @ApiModelProperty(value=\"").append(column.getComment()).append("\", required=true)").append("\n");
                }else{
                    fields.append("    @ApiModelProperty(\"").append(column.getComment()).append("\")").append("\n");
                    fields_dto.append("    @ApiModelProperty(\"").append(column.getComment()).append("\")").append("\n");
                }
            }
            // @Column(name="bind_status")
            String fieldname = DBHelper.buildFieldnameFromColumnname(column.getName());
            if(!fieldname.equals(column.getName())){
                hasColumnAnnotation = true;
                fields.append("    @Column(name=\"").append(column.getName()).append("\")").append("\n");
            }
            // private int bindStatus;
            if("Date".equals(javaType)){
                hasDate = true;
            }
            if("BigDecimal".equals(javaType)){
                hasBigDecimal = true;
            }
            fields.append("    private ").append(javaType).append(" ").append(fieldname).append(";").append("\n");
            fields_dto.append("    private ").append(javaType).append(" ").append(fieldname).append(";").append("\n");
            if(SimcoderRun.IS_GENERATE_BUILDER){
                fields_BuilderSetValues.append("        this.").append(fieldname).append(" = builder.").append(fieldname).append(";");
                fields_BuilderNoAnnotations.append("        private ").append(javaType).append(" ").append(fieldname).append(";");
            }
            // getter and setter
            methods.append("    public ").append(javaType).append(" get").append(StringUtils.capitalize(fieldname)).append("() {").append("\n");
            methods.append("        return this.").append(fieldname).append(";").append("\n");
            methods.append("    }").append("\n");
            methods.append("\n");
            methods.append("    public void set").append(StringUtils.capitalize(fieldname)).append("(").append(javaType).append(" ").append(fieldname).append(") {").append("\n");
            methods.append("        this.").append(fieldname).append(" = ").append(fieldname).append(";").append("\n");
            methods.append("    }");
            if(SimcoderRun.IS_GENERATE_BUILDER){
                methods_Builders.append("        public Builder ").append(fieldname).append("(").append(javaType).append(" ").append(fieldname).append(") {").append("\n");
                methods_Builders.append("            this.").append(fieldname).append(" = ").append(fieldname).append(";").append("\n");
                methods_Builders.append("            return this;").append("\n");
                methods_Builders.append("        }");
            }
            // toString()
            if (StringUtils.isBlank(fields_toString.toString())) {
                fields_toString.append("\"");
            } else {
                fields_toString.append("                \", ");
            }
            if ("String".equals(javaType)) {
                fields_toString.append(fieldname).append("=").append("'\" + ").append(fieldname).append(" + ").append("'\\''");
            } else {
                fields_toString.append(fieldname).append("=").append("\" + ").append(fieldname);
            }
            fields_toString.append(" +");
            /* 方法与方法直接都空一行，并且最后一个setter之后就不用换行了（最后面的创建时间和修改时间两个字段已经跳过了） */
            if(i+1 != columnList.size()-2){
                fields_toString.append("\n");
                methods.append("\n\n");
                if(SimcoderRun.IS_GENERATE_BUILDER){
                    fields_BuilderSetValues.append("\n");
                    fields_BuilderNoAnnotations.append("\n");
                    methods_Builders.append("\n");
                }
            }
            /* 收集属性，供分页查询时作为条件 */
            fieldnameMap.put(fieldname, javaType);
        }
        /*
         * 用户信息
         * Generated from seed-simcoder by 玄玉<https://jadyer.cn/> on 2017/9/5 14:40.
         */
        if(StringUtils.isNotBlank(tablecomment)){
            if(tablecomment.endsWith("表")){
                tablecomment = tablecomment.substring(0, tablecomment.length()-1);
            }
        }else{
            tablecomment = tablename;
        }
        /*
         * 设置Beetl共享变量（目前2.8.1版本：共享变量只能set一次，第二次set时会冲掉之前所有的，因为源码里是直接改变对象引用的）
         */
        String classname = DBHelper.buildClassnameFromTablename(tablename);
        Map<String, Object> sharedVars = new HashMap<>();
        sharedVars.put("CLASS_NAME", classname);
        sharedVars.put("CLASS_NAME_uncapitalize", StringUtils.uncapitalize(classname));
        sharedVars.put("TABLE_NAME", tablename);
        sharedVars.put("TABLE_NAME_convertpoint", (tablename.startsWith("t_") ? tablename.substring(2) : tablename).replaceAll("_", "."));
        sharedVars.put("fields", fields.toString());
        sharedVars.put("fields_dto", fields_dto.toString());
        sharedVars.put("fields_toString", fields_toString.toString());
        sharedVars.put("methods", methods.toString());
        sharedVars.put("tablecomment", tablecomment);
        sharedVars.put("fieldnameMap", fieldnameMap);
        sharedVars.put("hasDate", hasDate);
        sharedVars.put("hasBigDecimal", hasBigDecimal);
        sharedVars.put("hasColumnAnnotation", hasColumnAnnotation);
        sharedVars.put("hasNotNullAnnotation", hasNotNullAnnotation);
        sharedVars.put("hasNotBlankAnnotation", hasNotBlankAnnotation);
        sharedVars.put("hasNotBlankSizeAnnotation", hasNotBlankSizeAnnotation);
        if(SimcoderRun.IS_GENERATE_BUILDER){
            sharedVars.put("fields_BuilderSetValues", fields_BuilderSetValues.toString());
            sharedVars.put("fields_BuilderNoAnnotations", fields_BuilderNoAnnotations.toString());
            sharedVars.put("methods_Builders", methods_Builders.toString());
        }
        groupTemplate.setSharedVars(sharedVars);
        /*
         * 解析Beetl模板
         */
        try {
            String fileSeparator = System.getProperty("file.separator");
            String outBaseDir = FileSystemView.getFileSystemView().getHomeDirectory().getPath() + fileSeparator + "simcoder" + fileSeparator;
            if(SimcoderRun.IS_GENERATE_FEIGN_API){
                groupTemplate.getTemplate("feign-api/api.btl").renderTo(FileUtils.openOutputStream(new File(outBaseDir + "api" + fileSeparator + classname + "Api.java")));
                groupTemplate.getTemplate("feign-api/dto.btl").renderTo(FileUtils.openOutputStream(new File(outBaseDir + "dto" + fileSeparator + classname + "DTO.java")));
                groupTemplate.getTemplate("feign-api/model.btl").renderTo(FileUtils.openOutputStream(new File(outBaseDir + "model" + fileSeparator + classname + ".java")));
                groupTemplate.getTemplate("feign-api/controller.btl").renderTo(FileUtils.openOutputStream(new File(outBaseDir + "controller" + fileSeparator + classname + "Controller.java")));
            }else{
                groupTemplate.getTemplate("model.btl").renderTo(FileUtils.openOutputStream(new File(outBaseDir + "model" + fileSeparator + classname + ".java")));
                groupTemplate.getTemplate("controller.btl").renderTo(FileUtils.openOutputStream(new File(outBaseDir + "controller" + fileSeparator + classname + "Controller.java")));
            }
            groupTemplate.getTemplate("service.btl").renderTo(FileUtils.openOutputStream(new File(outBaseDir + "service" + fileSeparator + classname + "Service.java")));
            groupTemplate.getTemplate("repository.btl").renderTo(FileUtils.openOutputStream(new File(outBaseDir + "repository" + fileSeparator + classname + "Repository.java")));
        } catch (IOException e) {
            System.err.println("生成代码时发生异常，堆栈轨迹如下：");
            e.printStackTrace();
        }
    }
}