package com.company.openplatform.permission.domain;

public enum PermissionCode {
    CUSTOMER_BASE_READ("客户基础信息", "查询客户基础资料", "当前绑定客户", "包含脱敏联系信息"),
    ORDER_LIST_READ("订单列表", "查询订单列表", "当前绑定客户订单", "包含业务交易数据"),
    ORDER_DETAIL_READ("订单详情", "查询单笔订单详情", "当前绑定客户订单", "收货信息对外脱敏");
    public final String displayName, purpose, dataScope, sensitiveNotice;
    PermissionCode(String name, String purpose, String scope, String notice) { this.displayName=name; this.purpose=purpose; this.dataScope=scope; this.sensitiveNotice=notice; }
}
