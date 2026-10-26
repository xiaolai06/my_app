package com.example.dailylife

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import java.io.File

/** Uses the device font scale as configured by the runner; no business data is changed. */
class AppNavigationUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun fiveDestinationsKeepAccessibleNativeNavigationAtDeviceFontScale() {
        val roots=listOf("今天" to "home","记录" to "records","计划" to "plans","账本" to "ledger","功能箱" to "tools")
        compose.waitUntil(20000) {compose.onAllNodesWithTag("homeContent").fetchSemanticsNodes().isNotEmpty()}
        roots.forEach {(label,file)->
            compose.onNodeWithTag("root:"+label).performClick();ready()
            roots.forEach {(name,_)->compose.onNodeWithTag("root:"+name).assertIsDisplayed().assertHasClickAction()}
            compose.onNodeWithTag("root:"+label).assertIsSelected()
            val expected=when(label) {"今天"->"homeContent";"记录"->"recordSearch";"计划"->"planSearch";"账本"->"ledgerRoot";else->"toolsHome"}
            compose.onNodeWithTag(expected).assertExists()
            if(compose.activity.resources.configuration.fontScale>1.3f && label in setOf("记录","计划","账本")) {
                val action=when(label) {"记录"->"addRecord";"计划"->"addPlan";else->"addLedgerTransaction"}
                val content=when(label) {"记录"->"recordsContent";"计划"->"planTaskList";else->"ledgerOverview"}
                val actionBounds=compose.onNodeWithTag(action).fetchSemanticsNode().boundsInRoot
                val contentBounds=compose.onNodeWithTag(content).fetchSemanticsNode().boundsInRoot
                Assert.assertTrue("Large text action covers content: $label",contentBounds.bottom<=actionBounds.top+1f)
            }
            val density=compose.activity.resources.displayMetrics.density
            roots.forEach {(name,_)->val bounds=compose.onNodeWithTag("root:"+name).fetchSemanticsNode().boundsInRoot;Assert.assertTrue("Navigation target is narrower than 48 dp: $name",bounds.width>=48*density);Assert.assertTrue("Navigation target is shorter than 48 dp: $name",bounds.height>=48*density)}
            shot("navigation-"+file+"-font"+compose.activity.resources.configuration.fontScale+".png")
        }
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("toolFeature:focus"))
        compose.onNodeWithTag("toolFeature:focus").performClick();ready()
        compose.onNodeWithTag("root:计划").assertIsSelected()
        compose.onNodeWithTag("planTab:专注").assertIsSelected()
        compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
        ready();compose.onNodeWithTag("root:功能箱").assertIsSelected()
        compose.onNodeWithTag("root:计划").performClick();ready()
        compose.onNodeWithTag("planTab:习惯").performScrollTo().performClick()
        compose.onNodeWithTag("root:今天").performClick();ready()
        compose.onNodeWithTag("root:计划").performClick();ready()
        compose.onNodeWithTag("planTab:习惯").assertIsSelected()
    }
    private fun ready() {compose.waitUntil(15000) {compose.onAllNodesWithTag("appBusy").fetchSemanticsNodes().isEmpty()};compose.waitForIdle();if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) {compose.onNodeWithTag("dismissMessage").performClick();compose.waitForIdle()}}
    private fun shot(name:String) {Assert.assertTrue("System dialog covers app",compose.activity.hasWindowFocus());val i=InstrumentationRegistry.getInstrumentation();val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {File(it).apply {mkdirs()}} ?: compose.activity.getExternalFilesDir(null)!!;i.uiAutomation.takeScreenshot().let {bitmap->File(dir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
}
