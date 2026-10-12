package com.app.plugin.firbase

import com.app.plugin.AppPluginInternalApi
import com.app.plugin.iap.SubState
import com.app.plugin.iap.SubscriptionLifecycle
import com.app.plugin.logevent.LogEventServerHelper

/**
 * SPI core → mã game (2.1.0 G1, hợp đồng B): ba thứ `appplugin-game` cần mà core giữ `internal`. Chỉ ủy quyền, 0 trạng thái,
 * 0 I/O: country/userId cùng nguồn với cột `country`/`user_id` của mọi event (routing và dữ liệu đo không lệch nhau),
 * trạng thái thuê bao theo client (D28: không nhãn server).
 */
@AppPluginInternalApi
public object GameHost {
    /** Quốc gia hiện tại, như `LogEventServerHelper.currentCountry()`. */
    public fun country(): String = LogEventServerHelper.currentCountry()

    /** Id người chơi hiện tại, như `LogEventServerHelper.currentUserId()`. */
    public fun userId(): String = LogEventServerHelper.currentUserId()

    /** Trạng thái thuê bao [productId] phía client, như `SubscriptionLifecycle.clientStateFor`. */
    public fun subscriptionState(productId: String): SubState = SubscriptionLifecycle.clientStateFor(productId)
}
