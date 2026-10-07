package com.github.zzwtsy.easytierkt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsCanBeOpenedAndSystemBackReturnsToConnectionStatus() {
        composeRule.onNodeWithText("打开设置").performClick()
        composeRule.onNodeWithText("网络名").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("未连接").assertIsDisplayed()
    }

    @Test
    fun settingsDestinationSurvivesActivityRecreation() {
        composeRule.onNodeWithText("打开设置").performClick()
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("对等节点地址").assertIsDisplayed()
    }
}
