package com.example.photosessions

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PromptsTest {
    @Test fun notificationsHaveDistinctStableRoutesAndCancelIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prompts.channel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val one = Session(12, null, 1000, 2000, true)
        val two = one.copy(id = 13)
        Prompts.show(context, one, 3); Prompts.show(context, two, 5)
        val notifications = manager.activeNotifications
        assertEquals(2, notifications.size)
        notifications.forEach { entry ->
            val intent = shadowOf(entry.notification.contentIntent).savedIntent
            assertEquals(entry.tag.toLong(), intent.getLongExtra("session_id", 0))
            assertEquals("photosessions://name/${entry.tag}", intent.data.toString())
            assertEquals(MainActivity::class.java.name, intent.component?.className)
        }
        Prompts.show(context, one, 3)
        assertEquals(2, manager.activeNotifications.size)
        Prompts.cancel(context, 12)
        assertEquals("13", manager.activeNotifications.single().tag)
    }
    @Test fun disabledChannelBlocksPrompts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prompts.channel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(Prompts.CHANNEL)
        channel.importance = NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(channel)
        assertFalse(Prompts.allowed(context))
    }
}
