package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.CustomReminderEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.data.model.LoveBadgeEntity
import com.example.data.model.MilestoneEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineUserEntity
import com.example.data.model.ReminderCadenceEntity
import com.example.data.model.SecurityAuditLogEntity
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.UserAccountEntity

@Database(
  entities = [
    MilestoneEntity::class,
    GiftIdeaEntity::class,
    ChecklistItemEntity::class,
    CustomReminderEntity::class,
    ReminderCadenceEntity::class,
    CoupleProfileEntity::class,
    SharedMemoryEntity::class,
    LoveBadgeEntity::class,
    AnniversaryDateEntity::class,
    GiftReminderEntity::class,
    OnlineUserEntity::class,
    OnlineRelationshipEntity::class,
    OnlineInviteEntity::class,
    UserAccountEntity::class,
    SecurityAuditLogEntity::class
  ],
  version = 12,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun inLoveDao(): InLoveDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "inlove_database"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
        INSTANCE = instance
        instance
      }
    }
  }
}
