package com.example.dailylife

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.local.ToolEntry
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.ledger.LedgerSnapshot
import com.example.dailylife.feature.plans.PlansSnapshot
import com.example.dailylife.feature.tools.*
import com.example.dailylife.ui.DailyTheme
import com.example.dailylife.ui.ToolDetail
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ToolLinksUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun subscriptionManagementUsesWebHandlerAndKeepsOriginalDetail() {
        val app=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
        val vm=ToolsViewModel(app,SavedStateHandle());val store=ViewModelStore().apply {put("tools",vm)}
        val url="https://example.com/manage-subscription"
        val entry=ToolEntry(ownerId="local",kind="subscription",title="订阅跳转验证",payload=JSONObject().put("version",1).put("url",url).toString())
        var opened:String?=null;var businessNavigation=false
        try {
            compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {
                CompositionLocalProvider(LocalUriHandler provides object:UriHandler {override fun openUri(uri:String) {opened=uri}}) {
                    ToolDetail(ToolsUi(ready=true,data=ToolsSnapshot(entries=listOf(entry)),detailId=entry.id),vm,LedgerSnapshot(),PlansSnapshot(),{_,_->businessNavigation=true},{})
                }
            }}
            compose.onNodeWithText("打开原平台管理页面").performScrollTo().performClick()
            compose.runOnIdle {assertEquals(url,opened);assertFalse(businessNavigation)}
            compose.onNodeWithTag("backToolDetail").assertExists()
        } finally {compose.runOnIdle {store.clear()}}
    }
}
