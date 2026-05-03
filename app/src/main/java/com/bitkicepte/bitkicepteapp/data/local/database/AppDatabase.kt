package com.bitkicepte.bitkicepteapp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.bitkicepte.bitkicepteapp.data.local.dao.ActuatorEventDao
import com.bitkicepte.bitkicepteapp.data.local.dao.EnergyDao
import com.bitkicepte.bitkicepteapp.data.local.dao.PlantProfileDao
import com.bitkicepte.bitkicepteapp.data.local.dao.SensorReadingDao
import com.bitkicepte.bitkicepteapp.data.local.entity.ActuatorEvent
import com.bitkicepte.bitkicepteapp.data.local.entity.ActuatorType
import com.bitkicepte.bitkicepteapp.data.local.entity.ControlMode
import com.bitkicepte.bitkicepteapp.data.local.entity.EnergyDailySummary
import com.bitkicepte.bitkicepteapp.data.local.entity.EnergyReading
import com.bitkicepte.bitkicepteapp.data.local.entity.PlantProfile
import com.bitkicepte.bitkicepteapp.data.local.entity.SensorReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EnumConverters {
    @TypeConverter fun fromControlMode(v: ControlMode): String = v.name
    @TypeConverter fun toControlMode(v: String): ControlMode = ControlMode.valueOf(v)
    @TypeConverter fun fromActuatorType(v: ActuatorType): String = v.name
    @TypeConverter fun toActuatorType(v: String): ActuatorType = ActuatorType.valueOf(v)
}

@Database(
    entities = [
        SensorReading::class,
        ActuatorEvent::class,
        EnergyReading::class,
        EnergyDailySummary::class,
        PlantProfile::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sensorReadingDao(): SensorReadingDao
    abstract fun actuatorEventDao(): ActuatorEventDao
    abstract fun energyDao(): EnergyDao
    abstract fun plantProfileDao(): PlantProfileDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Ön tanımlı bitki profilleri
        val DEFAULT_PROFILES = listOf(
            PlantProfile(
                name = "Marul",
                targetDli = 14f,
                vpdMin = 0.8f, vpdMax = 1.2f,
                soilMinPercent = 40f,
                tempMinC = 15f, tempMaxC = 24f
            ),
            PlantProfile(
                name = "Domates",
                targetDli = 25f,
                vpdMin = 1.0f, vpdMax = 1.5f,
                soilMinPercent = 60f,
                tempMinC = 18f, tempMaxC = 28f
            ),
            PlantProfile(
                name = "Cilek",
                targetDli = 17f,
                vpdMin = 0.7f, vpdMax = 1.1f,
                soilMinPercent = 50f,
                tempMinC = 15f, tempMaxC = 26f
            ),
            PlantProfile(
                name = "Genel Sera",
                targetDli = 15f,
                vpdMin = 0.8f, vpdMax = 1.5f,
                soilMinPercent = 35f,
                tempMinC = 18f, tempMaxC = 28f
            )
        )

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bitkicepte.db"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // İlk açılışta varsayılan profilleri ekle
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.plantProfileDao()?.insertAll(DEFAULT_PROFILES)
                        }
                    }
                })
                .build().also { INSTANCE = it }
            }
    }
}
