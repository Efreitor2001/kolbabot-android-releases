package ru.kolbabot.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoreContractTest {
    private val sizes = listOf(
        JSONObject().put("max_weight_grams", 1000).put("price_kopecks", 20000),
        JSONObject().put("max_weight_grams", 5000).put("price_kopecks", 35000),
        JSONObject().put("max_weight_grams", 15000).put("price_kopecks", 60000)
    )
    @Test fun remainderUsesSmallerParcelTariff() {
        assertEquals(95000L, nativeDeliveryPrice(18000, sizes))
        assertEquals(60000L, nativeDeliveryPrice(15000, sizes))
    }
    @Test fun physicalZeroWeightStillUsesBaseParcel() {
        assertEquals(20000L, nativeDeliveryPrice(0, sizes))
        assertNull(nativeDeliveryPrice(100, emptyList()))
    }
    @Test fun oldServerPreservesThreeExistingPromotionsAndRealSettings() {
        val catalog = JSONObject("""{"promo_banner":{"visible":true,"referral_visible":true,"review_bonus_visible":true},"promo_settings":{"sausages":{"standard_percent":7,"premium_percent":12}},"referral_settings":{"percent":6},"review_bonus_settings":{"sausages":30,"minimum_characters":120}}""")
        val banners = storeBanners(catalog)
        assertEquals(listOf("sausages", "referral", "review-bonus"), banners.map { it.getString("id") })
        assertTrue(banners[0].getString("text").contains("7%"))
        assertTrue(banners[1].getString("text").contains("6%"))
        assertTrue(banners[2].getString("text").contains("30 колбасок"))
    }
    @Test fun explicitEmptyCarouselDoesNotRestoreHiddenBanners() {
        assertTrue(storeBanners(JSONObject("""{"banners":[],"promo_banner":{"visible":true}}""")).isEmpty())
    }
    @Test fun savedBannersKeepOrderAndRespectBooleanOrNumericVisibility() {
        val catalog = JSONObject("""{"banners":[{"id":"hidden-number","is_active":0},{"id":"later","is_active":true,"sort_order":20},{"id":"first","is_active":1,"sort_order":10},{"id":"hidden-bool","is_active":false}]}""")
        assertEquals(listOf("first", "later"), storeBanners(catalog).map { it.getString("id") })
    }
}
