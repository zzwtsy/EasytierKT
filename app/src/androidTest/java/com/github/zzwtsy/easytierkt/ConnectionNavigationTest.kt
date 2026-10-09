package com.github.zzwtsy.easytierkt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zzwtsy.easytierkt.data.connection.ConnectionPhase
import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionNavigationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val application get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestEasytierApplication
    private val profiles get() = application.appContainer.profileRepository

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(
                object : ExternalResource() {
                    override fun before() {
                        application.reset()
                    }
                },
            ).around(composeRule)

    /** 打开配置列表后触发系统返回，验证回到显示未连接的首页。 */
    @Test
    fun settingsCanBeOpenedAndSystemBackReturnsToConnectionStatus() {
        composeRule.onNodeWithContentDescription("管理配置").performClick()
        composeRule.onNodeWithText("网络配置").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("未连接").assertIsDisplayed()
    }

    /** 编辑公司配置并输入草稿后重建 Activity，验证编辑目标和未保存名称仍显示。 */
    @Test
    fun settingsDestinationSurvivesActivityRecreation() {
        composeRule.onNodeWithContentDescription("管理配置").performClick()
        composeRule.onNodeWithContentDescription("编辑配置 公司").performClick()
        composeRule.onNodeWithText("配置名称").performTextReplacement("公司备用")
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("公司备用").assertIsDisplayed()
        composeRule.onNodeWithText("office").assertIsDisplayed()
    }

    /** 在新增页填写并保存后，列表出现第三份配置，原选中项仍是公司。 */
    @Test
    fun createsAnotherProfileWithoutChangingSelection() {
        composeRule.onNodeWithContentDescription("管理配置").performClick()
        composeRule.onNodeWithText("新增配置").performClick()
        composeRule.onNodeWithText("配置名称").performTextInput("测试线路")
        composeRule.onNodeWithText("网络名").performScrollTo().performTextInput("test-network")
        composeRule.onNodeWithText("保存配置").performClick()
        composeRule.onNodeWithText("测试线路").assertIsDisplayed()
        assertEquals(
            3,
            profiles.state.value.document
                ?.profiles
                ?.size,
        )
        assertEquals(
            "a",
            profiles.state.value.document
                ?.selectedProfileId,
        )
    }

    /** 选择家里并确认删除该配置后，列表只剩公司且选中 ID 清空。 */
    @Test
    fun deletesSelectedProfileAndRequiresNewSelection() {
        composeRule.onNodeWithContentDescription("管理配置").performClick()
        composeRule.onNodeWithTag("select-b").performClick()
        composeRule.onNodeWithTag("select-b").assertIsSelected()
        composeRule.onNodeWithContentDescription("删除配置 家里").performClick()
        composeRule.onNode(hasText("删除") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNodeWithText("请选择一份配置后再连接。").assertIsDisplayed()
        assertNull(
            profiles.state.value.document
                ?.selectedProfileId,
        )
        assertEquals(
            listOf("a"),
            profiles.state.value.document
                ?.profiles
                ?.map { it.id },
        )
    }

    /** 配置处于运行状态时，选择其他配置和删除运行配置的控件禁用，编辑仍可进入。 */
    @Test
    fun activeProfileLocksSwitchingAndDeletion() {
        runBlocking { profiles.reserveSession("a") }
        application.connections.status.value = ConnectionStatus(phase = ConnectionPhase.CONNECTED, profileId = "a", profileName = "公司")
        composeRule.onNodeWithContentDescription("管理配置").performClick()
        composeRule.onNodeWithTag("select-b").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("删除配置 公司").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("编辑配置 公司").performClick()
        composeRule.onNodeWithText("正在运行此配置。保存不会立即改变当前连接；下次手动连接或系统恢复时使用新参数。").performScrollTo().assertIsDisplayed()
    }

    /** 编辑有未保存内容时触发系统返回，确认放弃后返回列表且原配置未修改。 */
    @Test
    fun backPromptsBeforeDiscardingUnsavedChanges() {
        composeRule.onNodeWithContentDescription("管理配置").performClick()
        composeRule.onNodeWithContentDescription("编辑配置 公司").performClick()
        composeRule.onNodeWithText("配置名称").performTextInput("草稿")
        // 等待草稿状态收集及返回拦截更新，再模拟用户的下一次返回操作。
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("放弃修改？").assertIsDisplayed()
        composeRule.onNodeWithText("放弃修改").performClick()
        composeRule.onNodeWithText("网络配置").assertIsDisplayed()
        assertEquals(
            "公司",
            profiles.state.value.document
                ?.profiles
                ?.first()
                ?.displayName,
        )
    }

    /** 配置列表读取失败时禁止新增，恢复读取后重试重新展示已有配置。 */
    @Test
    fun readFailureOffersRetryWithoutOverwritingProfiles() {
        application.store.failRead = true
        composeRule.onNodeWithContentDescription("管理配置").performClick()
        composeRule.onNodeWithText("新增配置").assertIsNotEnabled()
        composeRule.onNodeWithText("无法读取已保存的加密配置。请重试；原数据不会被覆盖。").assertIsDisplayed()
        application.store.failRead = false
        composeRule.onNodeWithText("重试读取").performClick()
        composeRule.onNodeWithText("公司").assertIsDisplayed()
        assertEquals(
            2,
            profiles.state.value.document
                ?.profiles
                ?.size,
        )
    }
}
