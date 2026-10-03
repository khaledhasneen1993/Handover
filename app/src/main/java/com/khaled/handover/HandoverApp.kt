package com.khaled.handover

import android.app.Application
import androidx.room.Room
import com.khaled.handover.data.HandoverDb
import com.khaled.handover.data.InspectionRepository
import com.khaled.handover.backup.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class HandoverApp : Application() {
    val db by lazy { Room.databaseBuilder(applicationContext, HandoverDb::class.java, "handover-v1.db").build() }
    val repository by lazy { InspectionRepository(applicationContext, db) }
    private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    /** Startup barrier: no restored data is shown until interrupted work is reconciled. */
    val recovery by lazy { appScope.async {
        BackupManager(applicationContext,repository).recoverInterruptedRestore()
        repository.recoverOrphanOriginals()
    } }
    override fun onCreate() {
        super.onCreate()
        recovery.start()
    }
}
