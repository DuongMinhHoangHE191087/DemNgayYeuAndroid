package com.app.plugin.ads

import org.junit.Test

class MediationAvailabilityTest {
    @Test fun bigoAdMobCustomEventIsPackaged() {
        Class.forName("com.google.ads.mediation.bigoads.BigoAdsCustomEvent", false, javaClass.classLoader)
    }
    @Test fun pangleAdapterIsPackaged() {
        Class.forName("com.google.ads.mediation.pangle.PangleMediationAdapter", false, javaClass.classLoader)
    }
}
