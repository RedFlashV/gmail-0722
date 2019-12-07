package com.atguigu.core.bean;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;


@ApiModel
@Data
public class QueryCondition {


    @ApiModelProperty(name = "page",value = "页码",required = false)
    private Long page;

    @ApiModelProperty(name = "limit",value = "每页大小",required = false)
    private Long limit;

    @ApiModelProperty(name = "sidx",value = "排序的字段",required = false)
    private String sidx;

    @ApiModelProperty(name = "order",value = "排序的顺序",required = false)
    private String order;

    @ApiModelProperty(name = "asc",value = "系统默认排序",required = false)
    private String asc = "asc";
/*    @ApiModelProperty()用于方法，字段； 表示对model属性的说明或者数据操作更改
    value–字段说明
    name–重写属性名字
    dataType–重写属性类型
    required–是否必填
    example–举例说明
    hidden–隐藏*/
    @ApiModelProperty(name = "key",value = "搜索关键字",required = false)
    private  String key;
}
