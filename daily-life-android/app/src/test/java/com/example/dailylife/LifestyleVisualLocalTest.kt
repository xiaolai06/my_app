package com.example.dailylife

import android.content.*
import android.database.MatrixCursor
import android.graphics.*
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.tools.*
import com.example.dailylife.ui.toolPhotos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LifestyleVisualLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val tools get()=ViewModelProvider(compose.activity)[ToolsViewModel::class.java]
    private val records get()=ViewModelProvider(compose.activity)[RecordsViewModel::class.java]
    private val originals=mutableMapOf<String,File>()
    private fun await(check:()->Boolean) {compose.waitUntil(30_000) {compose.waitForIdle();check()};compose.waitForIdle()}
    @Before fun setup() {
        await {tools.ui.value.ready && records.ui.value.ready && !tools.ui.value.busy}
        runBlocking {
            app.preferences.set(Appearance(theme="light",reducedMotion=true))
            listOf("wardrobe","outfit","item","checklist","anniversary","subscription","bill","loan","project").forEach {
                app.preferences.setToolLayout(it,if(it in setOf("wardrobe","outfit")) "grid" else "list")
            }
        }
        await {tools.ui.value.layouts["wardrobe"]=="grid"}
        ShadowContentResolver.registerProviderInternal("local-photo-fixture",object:ContentProvider() {
            override fun onCreate()=true
            override fun getType(uri:Uri)=if(uri.lastPathSegment=="wrong") "text/plain" else "image/jpeg"
            override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?)=
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).apply {val f=originals.getValue(uri.lastPathSegment!!);addRow(arrayOf(f.name,f.length()))}
            override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(originals.getValue(uri.lastPathSegment!!),ParcelFileDescriptor.MODE_READ_ONLY)
            override fun insert(uri:Uri,values:ContentValues?):Uri?=null
            override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
            override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
        }.apply {attachInfo(app,android.content.pm.ProviderInfo().apply {authority="local-photo-fixture"})})
        compose.onNodeWithTag("root:功能箱").performClick()
    }
    @After fun close() {compose.activity.finish();app.backgroundScope.cancel();app.database.close()}
    private fun photo(key:String,color:Int):Uri {
        val file=File(app.cacheDir,"fixture-$key.jpg")
        val bitmap=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(242,240,235))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color}
        val shirt=Path().apply {moveTo(190f,160f);lineTo(80f,220f);lineTo(30f,390f);lineTo(140f,430f);lineTo(170f,350f);lineTo(170f,680f);lineTo(430f,680f);lineTo(430f,350f);lineTo(460f,430f);lineTo(570f,390f);lineTo(520f,220f);lineTo(410f,160f);cubicTo(370f,260f,230f,260f,190f,160f);close()}
        canvas.drawPath(shirt,paint)
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
        originals[key]=file;return Uri.parse("content://local-photo-fixture/$key")
    }
    private fun shot(name:String) {
        compose.waitUntil(10_000) {compose.onAllNodesWithText("载入图片…").fetchSemanticsNodes().isEmpty()}
        compose.waitForIdle()
        val file=File(System.getProperty("myLife.lifestyleOutput","build/reports/lifestyle"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    private fun page(value:String) {compose.runOnIdle {tools.page(value)};await {!tools.ui.value.busy && tools.ui.value.page==value}}
    private fun beginClothing() {compose.runOnIdle {tools.beginClothing()};await {tools.ui.value.editor!=null && !tools.ui.value.busy}}
    private fun seedCloset():List<ToolForm> = runBlocking {
        val names=listOf("雾蓝衬衫","奶油色针织衫","日常白色上衣","周末休闲外套","春日轻薄衬衫","旅行时穿的长款外套")
        val colors=listOf(Color.rgb(82,118,157),Color.rgb(186,162,126),Color.rgb(180,184,191),Color.rgb(108,139,120),Color.rgb(170,127,149),Color.rgb(119,114,155))
        names.mapIndexed {i,name->
            val f=ToolForm("item").set("title",name).set("wardrobe","true").set("clothingType",if(i<3) "top" else "coat")
                .set("occasion",if(i%2==0) "上班" else "周末").set("styleTags",if(i%2==0) "通勤,简约" else "休闲,旅行")
            app.tools.prepare(f);app.tools.importFile(f.id,photo("polish-$i",colors[i]),true);app.tools.save(f);f
        }.also {forms->
            repeat(3) {i->val f=ToolForm("outfit").set("title",listOf("周一通勤","周末散步","轻松出游")[i]).set("occasion",if(i==0) "上班" else "周末")
                .set("styleTags",if(i==0) "通勤" else "休闲").set("items",forms.drop(i).take(2).joinToString(",") {it.id})
                app.tools.prepare(f);app.tools.save(f)}
            await {tools.ui.value.data.entries.size==9 && records.ui.value.snapshot.attachments.size==6}
        }
    }
    @Test fun polishedClosetKeepsTwoEqualCardsAndSupportsPinFavoriteAndOutfitSwitch() {
        seedCloset();page("wardrobe")
        val order=WardrobeCatalog(tools.ui.value.data).filter(false,WardrobeFilter())
        val left=compose.onNodeWithTag("wardrobeEntry:"+order[0].id).fetchSemanticsNode().boundsInRoot
        val right=compose.onNodeWithTag("wardrobeEntry:"+order[1].id).fetchSemanticsNode().boundsInRoot
        assertEquals(left.top,right.top,1f);assertEquals(left.width,right.width,1f);assertEquals(left.height,right.height,1f);assertTrue(right.left>left.right)
        shot("polish-01-衣物双列")
        compose.onNodeWithTag("wardrobeMore:"+order[1].id).performClick()
        compose.onNodeWithTag("wardrobePinAction").performClick()
        await {!tools.ui.value.busy && tools.ui.value.data.entries.first {it.id==order[1].id}.pinned}
        compose.onNodeWithTag("wardrobeCollection:pinned").performClick()
        compose.onNodeWithText("1 件衣物").assertIsDisplayed()
        compose.onNodeWithTag("wardrobeMore:"+order[1].id).performClick()
        compose.onNodeWithTag("wardrobeFavoriteAction").performClick()
        await {!tools.ui.value.busy && tools.ui.value.data.entries.first {it.id==order[1].id}.isFavorite()}
        compose.onNodeWithTag("wardrobeCollection:favorites").performClick();compose.onNodeWithText("1 件衣物").assertIsDisplayed()
        shot("polish-02-置顶与收藏")
        compose.onNodeWithTag("wardrobeTab:outfits").performClick();await {tools.ui.value.page=="outfit"}
        compose.onNodeWithText("3 套穿搭").assertIsDisplayed();shot("polish-03-穿搭组合")
        compose.onNodeWithTag("wardrobeTab:clothes").performClick();await {tools.ui.value.page=="wardrobe"}
        compose.onNodeWithTag("wardrobeCollection:favorites").assertIsSelected()
    }
    @Test fun wardrobeFilterCancelDoesNotApplyAndTagsCanBeSavedFromEditor() {
        val forms=seedCloset();page("wardrobe")
        compose.onNodeWithTag("wardrobeFilters").performClick()
        compose.onNodeWithText("场景",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("上班",substring=false).performClick()
        compose.onNodeWithText("取消",substring=false).performClick()
        compose.onNodeWithText("6 件衣物").assertIsDisplayed()
        compose.onNodeWithTag("wardrobeFilters").performClick()
        compose.onNodeWithText("搭配归属").performScrollTo().performClick()
        compose.onNodeWithText("独立单品",substring=false).performClick()
        compose.onNodeWithText("应用筛选").performClick()
        compose.onNodeWithText("2 件衣物").assertIsDisplayed();shot("polish-04-独立单品筛选")
        compose.runOnIdle {tools.begin("item",forms.last().id)};await {tools.ui.value.editor!=null && !tools.ui.value.busy}
        compose.onNodeWithTag("wardrobeTags").performScrollTo().performTextReplacement("舒适，旅行")
        compose.onNodeWithTag("saveTool").performClick()
        await {tools.ui.value.editor==null && !tools.ui.value.busy}
        assertEquals(listOf("舒适","旅行"),tools.ui.value.data.entries.first {it.id==forms.last().id}.styleTags())
        compose.onNodeWithText("# 舒适").performScrollTo().assertIsDisplayed()
    }
    @Test fun importedPhotosStayLocalAndCoverWorksAcrossEditorGalleryAndBothLayouts() {
        page("wardrobe");beginClothing()
        compose.onNodeWithTag("toolTitle").performTextInput("周末蓝衬衫")
        val uris=listOf(photo("front",Color.rgb(82,118,157)),photo("back",Color.rgb(108,145,182)))
        compose.runOnIdle {tools.importFiles(uris,true)}
        val draftId=tools.ui.value.editor!!.id
        await {!tools.ui.value.busy && records.ui.value.snapshot.attachmentsFor("tool_draft",draftId).size==2}
        val attachments=records.ui.value.snapshot.attachmentsFor("tool_draft",draftId)
        originals.values.forEach {assertTrue(it.delete())}
        assertTrue(attachments.all {records.repository.files.file(it).exists()})
        compose.onNodeWithTag("editPhoto:"+attachments[1].id).performScrollTo().performClick()
        compose.onNodeWithText("设为封面").performClick()
        await {!tools.ui.value.busy && records.ui.value.snapshot.attachmentsFor("tool_draft",draftId).first().id==attachments[1].id}
        compose.onNodeWithTag("toolPhotos").performScrollTo();shot("01-照片编辑与封面")
        compose.onNodeWithTag("saveTool").performClick()
        await {tools.ui.value.editor==null && tools.ui.value.data.entries.any {it.id==draftId} && !tools.ui.value.busy}
        assertEquals(attachments[1].id,records.ui.value.snapshot.attachmentsFor("tool",draftId).first().id)
        compose.onNodeWithTag("toolPhotoGallery").performScrollTo().assertIsDisplayed();shot("02-照片详情")
        compose.onNodeWithTag("galleryPhoto:1").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithTag("backToolDetail").performClick()
        compose.onNodeWithTag("wardrobeEntry:"+draftId).assertIsDisplayed();shot("03-衣橱卡片")
        compose.onNodeWithTag("layout:list").performClick();await {tools.ui.value.layouts["wardrobe"]=="list"}
        compose.onNodeWithTag("layout:list").assertIsSelected();shot("04-衣橱列表")
        page("home");page("wardrobe");compose.onNodeWithTag("layout:list").assertIsSelected()
        assertEquals("list",runBlocking {PreferencesRepository(app).toolLayouts.first()["wardrobe"]})
        assertTrue(app.filesDir.walkTopDown().none {it.extension=="jpg"}) // imported originals use internal attachment IDs
    }
    @Test fun outfitFallbackAvoidsDeletedClothesAndOwnPhotoTakesPriority()=runBlocking {
        val uri=photo("look",Color.rgb(132,108,150))
        val item=ToolForm("item").set("title","紫色外套").set("wardrobe","true")
        app.tools.prepare(item);app.tools.importFile(item.id,uri,true);app.tools.save(item)
        val outfit=ToolForm("outfit").set("title","周末散步").set("occasion","公园").set("items",item.id)
        app.tools.prepare(outfit);app.tools.save(outfit)
        await {tools.ui.value.data.entries.size==2 && records.ui.value.snapshot.attachments.isNotEmpty()}
        val entry=tools.ui.value.data.entries.first {it.id==outfit.id}
        assertEquals(1,toolPhotos(entry,tools.ui.value.data,records.ui.value.snapshot).size)
        app.tools.manage(item.id,"trash");await {tools.ui.value.data.entries.first {it.id==item.id}.deletedAt!=null}
        assertTrue(toolPhotos(entry,tools.ui.value.data,records.ui.value.snapshot).isEmpty())
        app.tools.manage(item.id,"restore")
        val edit=app.tools.form(outfit.id);app.tools.prepare(edit);app.tools.importFile(outfit.id,uri,true);app.tools.save(edit)
        await {records.ui.value.snapshot.attachmentsFor("tool",outfit.id).isNotEmpty()}
        assertEquals(records.ui.value.snapshot.attachmentsFor("tool",outfit.id).first().id,toolPhotos(entry,tools.ui.value.data,records.ui.value.snapshot).first().id)
        page("outfit");shot("05-穿搭卡片")
        compose.onNodeWithTag("wardrobeEntry:"+outfit.id).performClick();shot("06-穿搭与单品")
    }
    @Test fun photoOnlyImportRejectsNonPhotosWithoutLeavingAnOrphan() {
        beginClothing();val uri=photo("wrong",Color.BLUE)
        val before=records.repository.files.root.listFiles().orEmpty().size
        compose.runOnIdle {tools.importFiles(listOf(uri),true)}
        await {!tools.ui.value.busy && tools.ui.value.error!=null}
        assertTrue(records.ui.value.snapshot.attachmentsFor("tool_draft",tools.ui.value.editor!!.id).isEmpty())
        assertEquals(before,records.repository.files.root.listFiles().orEmpty().size)
        assertNotNull(tools.ui.value.editor)
    }
    @Test fun appearanceShowsVisualThemesAndPersistsNicknameWithoutTechnicalFooter() {
        compose.onNodeWithTag("appSettings").performClick()
        compose.onNodeWithTag("theme:light").assertIsSelected();shot("07-外观预览")
        compose.onNodeWithTag("theme:dark").performClick();await {records.ui.value.appearance.theme=="dark"}
        compose.onNodeWithTag("theme:dark").assertIsSelected();shot("08-深色外观")
        compose.onNodeWithText("核心数据在本机",substring=true).assertDoesNotExist()
        compose.onNodeWithText("0.5.0",substring=true).assertDoesNotExist()
        compose.onNodeWithText("增强磨砂底色").performScrollTo().performClick()
        await {records.ui.value.appearance.frosted}
        compose.onNodeWithTag("editNickname").performScrollTo().performClick()
        compose.onNodeWithTag("nicknameInput").performTextInput("小林")
        compose.onNodeWithText("保存",substring=false).performClick()
        await {runBlocking {app.preferences.dashboard.first().nickname=="小林"}}
        compose.onNodeWithText("关闭",substring=false).performClick()
        compose.onNodeWithText("小林",substring=false).assertIsDisplayed()
        compose.onNodeWithTag("personalBackup").performClick();shot("09-备份分组")
        compose.onNodeWithTag("backup:records").assertIsDisplayed()
    }
    @Test fun everyToolLayoutSwitchRetainsEntriesAndKeepsPreferencesIndependent()=runBlocking {
        mapOf("checklist" to "周末出游清单","anniversary" to "第一次旅行","subscription" to "音乐会员","bill" to "家庭网络","item" to "随身相机","loan" to "借出的旅行相机","project" to "秋日旅行").forEach {(kind,title)->
            val f=ToolForm(kind).set("title",title).set("date",LocalDate.now().toString()).set("person","朋友").set("amount","12.50")
            app.tools.prepare(f);app.tools.save(f)
            await {tools.ui.value.data.entries.any {it.id==f.id}}
            page(kind)
            compose.onNodeWithTag("layout:grid").performClick();await {tools.ui.value.layouts[kind]=="grid"}
            compose.onNodeWithTag("toolsList").performScrollToNode(hasTestTag("toolEntry:"+f.id))
            compose.onNodeWithTag("toolEntry:"+f.id).assertIsDisplayed();shot("tool-"+kind)
            compose.onNodeWithTag("toolsList").performScrollToNode(hasTestTag("layout:list"))
            compose.onNodeWithTag("layout:list").performClick();await {tools.ui.value.layouts[kind]=="list"}
        }
        assertEquals(7,tools.ui.value.data.entries.size)
        assertEquals("grid",tools.ui.value.layouts["wardrobe"])
    }
    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontSettingsAndCollectionControlsRemainUsable() {
        val entry=ToolForm("item").set("title","秋天喜欢的蓝色衬衫").set("wardrobe","true")
        runBlocking {app.tools.prepare(entry);app.tools.importFile(entry.id,photo("large",Color.rgb(82,118,157)),true);app.tools.save(entry)}
        await {tools.ui.value.data.entries.any {it.id==entry.id}}
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics)
            compose.activity.recreate()
        }
        await {tools.ui.value.ready && records.ui.value.ready && !tools.ui.value.busy}
        compose.onNodeWithTag("root:功能箱").performClick()
        compose.onNodeWithTag("appSettings").performClick()
        compose.onNodeWithTag("theme:dark").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        await {records.ui.value.appearance.theme=="dark"};shot("10-大字体外观")
        compose.onNodeWithText("关闭").performClick();page("wardrobe")
        compose.onNodeWithTag("layout:list").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        await {tools.ui.value.layouts["wardrobe"]=="list"}
        compose.onNodeWithTag("wardrobeGrid").performScrollToNode(hasTestTag("wardrobeEntry:"+entry.id))
        compose.onNodeWithTag("wardrobeEntry:"+entry.id).assertIsDisplayed();shot("11-大字体衣橱")
    }
}
