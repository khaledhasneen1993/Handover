package com.khaled.handover

import android.app.Application
import androidx.room.Room
import com.khaled.handover.data.HandoverDb
import com.khaled.handover.data.InspectionRepository

class HandoverApp : Application() {
    val db by lazy { Room.databaseBuilder(applicationContext, HandoverDb::class.java, "handover-v1.db").build() }
    val repository by lazy { InspectionRepository(applicationContext, db) }
}
