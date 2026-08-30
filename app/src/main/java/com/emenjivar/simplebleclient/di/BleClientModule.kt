package com.emenjivar.simplebleclient.di

import android.content.Context
import com.emenjivar.simplebleclient.BuildConfig
import com.emenjivar.simplebleclient.ble.BleNotifications
import com.emenjivar.simplebleclient.ble.BleOperationQueue
import com.emenjivar.simplebleclient.ble.BleScanner
import com.emenjivar.simplebleclient.ble.BleScannerImp
import com.emenjivar.simplebleclient.ble.BleClient
import com.emenjivar.simplebleclient.ble.RaspberryBleClient
import com.emenjivar.simplebleclient.ble.mock.MockBleDataSource
import com.emenjivar.simplebleclient.ble.mock.MockBleClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BleClientModule {

    @Provides
    @Singleton
    fun providesBleScanner(
        @ApplicationContext context: Context
    ): BleScanner = BleScannerImp(context)

    @Provides
    @Singleton
    fun providesMockBleDataSource(): MockBleDataSource = MockBleDataSource()

    /**
     * Picks the BLE implementation from the active product flavor.
     * `raspberry` → real GATT stack, anything else (`mock`) → in-memory fake.
     */
    @Provides
    @Singleton
    fun providesCustomBleManager(
        @ApplicationContext context: Context,
        bleNotifications: BleNotifications,
        bleOperationQueue: BleOperationQueue,
        scanner: BleScanner,
        mockBleDataSource: MockBleDataSource
    ): BleClient = when (BuildConfig.FLAVOR) {
        "raspberry" -> RaspberryBleClient(
            context = context,
            bleNotifications = bleNotifications,
            bleOperationQueue = bleOperationQueue,
            scanner = scanner
        )

        else -> MockBleClient(
            bleNotifications = bleNotifications,
            mockBleDataSource = mockBleDataSource
        )
    }
}
