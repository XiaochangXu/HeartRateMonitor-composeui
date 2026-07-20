package com.github.heartratemonitor_compose.ble

import com.juul.kable.Filter


object BleConstants {
    const val HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
    const val HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID = "00002a37-0000-1000-8000-00805f9b34fb"

    val heartRateServiceFilter = Filter.Service(HEART_RATE_SERVICE_UUID.toUuid())
}