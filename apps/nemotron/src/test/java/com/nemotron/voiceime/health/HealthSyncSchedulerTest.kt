package com.nemotron.voiceime.health

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

/** Execute the real scheduler with an in-memory Android scheduling boundary. */
class HealthSyncSchedulerTest {
    private class Environment(initialEnabled: Boolean = false) : AutoCloseable {
        val context = mock(Context::class.java)
        val scheduler = mock(JobScheduler::class.java)
        val preferences = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
        val pending = mutableMapOf<Int, JobInfo>()
        val starts = mutableListOf<Int>()
        var enabled = initialEnabled
        var writes = 0
        private var editedValue = initialEnabled
        private val builders = mockConstruction(JobInfo.Builder::class.java,
            withSettings().defaultAnswer(RETURNS_SELF)) { builder, construction ->
            val id = construction.arguments()[0] as Int
            val info = mock(JobInfo::class.java)
            `when`(info.id).thenReturn(id)
            `when`(builder.build()).thenReturn(info)
        }

        init {
            `when`(context.getSharedPreferences(HealthSyncScheduler.PREFERENCES, Context.MODE_PRIVATE)).thenReturn(preferences)
            `when`(context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(scheduler)
            `when`(preferences.getBoolean(HealthSyncScheduler.ENABLED, false)).thenAnswer { enabled }
            `when`(preferences.edit()).thenReturn(editor)
            `when`(editor.putBoolean(eq(HealthSyncScheduler.ENABLED), anyBoolean())).thenAnswer {
                editedValue = it.getArgument(1); editor
            }
            `when`(editor.commit()).thenAnswer { enabled = editedValue; writes++; true }
            `when`(scheduler.getPendingJob(anyInt())).thenAnswer { pending[it.getArgument<Int>(0)] }
            `when`(scheduler.schedule(any(JobInfo::class.java))).thenAnswer {
                val job = it.getArgument<JobInfo>(0)
                pending[job.id] = job; starts += job.id; JobScheduler.RESULT_SUCCESS
            }
            doAnswer { pending.remove(it.getArgument<Int>(0)); null }.`when`(scheduler).cancel(anyInt())
        }

        override fun close() { builders.close() }
    }

    @Test fun repeatedPanelRendersScheduleOnlyOneImmediateExport() {
        Environment().use { env ->
            repeat(100) { HealthSyncScheduler.enable(env.context) }
            assertEquals(listOf(3403, 3404), env.starts)
            assertEquals(1, env.writes)
            assertTrue(env.enabled)
        }
    }

    @Test fun aRealOffOnTransitionSchedulesAnotherImmediateExport() {
        Environment().use { env ->
            HealthSyncScheduler.enable(env.context)
            HealthSyncScheduler.disable(env.context)
            assertTrue(env.pending.isEmpty())
            HealthSyncScheduler.enable(env.context)
            assertEquals(listOf(3403, 3404, 3403, 3404), env.starts)
            assertEquals(3, env.writes)
        }
    }

    @Test fun alreadyEnabledStateRestoresPeriodicJobWithoutNewImmediateExport() {
        Environment(initialEnabled = true).use { env ->
            HealthSyncScheduler.enable(env.context)
            assertEquals(listOf(3403), env.starts)
            assertEquals(0, env.writes)
        }
    }

    @Test fun repeatedDisableStillCancelsStalePersistedJobs() {
        Environment().use { env ->
            HealthSyncScheduler.disable(env.context)
            verify(env.scheduler).cancel(3403)
            verify(env.scheduler).cancel(3404)
            assertEquals(0, env.writes)
            assertFalse(env.enabled)
        }
    }
}
