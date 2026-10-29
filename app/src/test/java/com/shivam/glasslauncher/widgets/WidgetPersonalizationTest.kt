package com.shivam.glasslauncher.widgets

import org.junit.Assert.*
import org.junit.Test

class WidgetPersonalizationTest {
    @Test fun hindiQuoteAndAttributionSurviveBackupRoundTrip() {
        val widget = WidgetInstance("quote", WidgetKind.QUOTES, WidgetSize.LARGE,
            mapOf(WidgetKeys.QUOTE_TEXT to QuoteDefaults.TEXT, WidgetKeys.QUOTE_AUTHOR to "मेरी डायरी"))
        val state = WidgetState(mapOf(widget.id to widget), today = listOf(widget.id))
        val restored = WidgetState.fromJson(state.toJson())
        assertEquals(state, restored)
        assertTrue(restored.instances.getValue("quote")[WidgetKeys.QUOTE_TEXT]!!.endsWith("है।"))
    }

    @Test fun providerBlendIsOptInAndRestoredPerInstance() {
        val original = WidgetInstance("provider", WidgetKind.APP_WIDGET, WidgetSize.MEDIUM)
        assertFalse(ProviderGlass.enabled(original))
        val enabled = original.copy(config = mapOf(WidgetKeys.PROVIDER_GLASS to "1", WidgetKeys.PROVIDER_BACKGROUND to "0.4"))
        val restored = WidgetState.fromJson(WidgetState(mapOf(enabled.id to enabled)).toJson()).instances.getValue(enabled.id)
        assertTrue(ProviderGlass.enabled(restored))
        assertEquals(0.4f, ProviderGlass.backgroundOpacity(restored), 0.001f)
        assertFalse(ProviderGlass.enabled(original))
    }

    @Test fun importedOpacityCannotProduceInvalidDrawableAlpha() {
        val widget = WidgetInstance("provider", WidgetKind.APP_WIDGET, WidgetSize.MEDIUM)
        for (value in listOf("NaN", "Infinity", "-Infinity", "not a number")) {
            assertEquals(0.25f, ProviderGlass.backgroundOpacity(widget.copy(config = mapOf(WidgetKeys.PROVIDER_BACKGROUND to value))), 0.001f)
        }
        assertEquals(0f, ProviderGlass.backgroundOpacity(widget.copy(config = mapOf(WidgetKeys.PROVIDER_BACKGROUND to "-2"))), 0.001f)
        assertEquals(1f, ProviderGlass.backgroundOpacity(widget.copy(config = mapOf(WidgetKeys.PROVIDER_BACKGROUND to "2"))), 0.001f)
    }
}
