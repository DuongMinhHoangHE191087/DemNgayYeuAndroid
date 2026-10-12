package com.google.android.gms.ads

import android.app.Activity

/** 2.4.1 HOSTFIX (D36): Activity mang tên gói của SDK quảng cáo (như `AdActivity`) — không bao giờ được nhận làm host. */
class HostFixFakeAdActivity : Activity()
