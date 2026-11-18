package com.aura.family.telemetry

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

data class SimSlotStatus(
    val slotIndex: Int,
    val isDetected: Boolean = false,
    val subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    val carrierName: String = "",
    val currentDbm: Int? = null
)

data class DualSimStatus(
    val sim1: SimSlotStatus = SimSlotStatus(slotIndex = 0),
    val sim2: SimSlotStatus = SimSlotStatus(slotIndex = 1)
)

class SimSignalMonitor(private val context: Context) {

    private val _dualSimStatus = MutableStateFlow(DualSimStatus())
    val dualSimStatus: StateFlow<DualSimStatus> = _dualSimStatus.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeCallbacksApi31 = ConcurrentHashMap<Int, TelephonyCallback>()

    @Suppress("DEPRECATION")
    private val activeListenersLegacy = ConcurrentHashMap<Int, PhoneStateListener>()

    private var subscriptionsChangedListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var onSignalSampleListener: ((slotIndex: Int, dbm: Int) -> Unit)? = null
    private var isStarted = false

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun refreshSubscriptions() {
        if (!hasPhoneStatePermission()) {
            _dualSimStatus.value = DualSimStatus()
            return
        }

        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val activeList: List<SubscriptionInfo> = try {
            subManager?.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }

        val slot0 = activeList.firstOrNull { it.simSlotIndex == 0 }
        val slot1 = activeList.firstOrNull { it.simSlotIndex == 1 }

        _dualSimStatus.update { current ->
            current.copy(
                sim1 = SimSlotStatus(
                    slotIndex = 0,
                    isDetected = slot0 != null,
                    subscriptionId = slot0?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                    carrierName = slot0?.carrierName?.toString().orEmpty(),
                    currentDbm = if (slot0 != null) current.sim1.currentDbm else null
                ),
                sim2 = SimSlotStatus(
                    slotIndex = 1,
                    isDetected = slot1 != null,
                    subscriptionId = slot1?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                    carrierName = slot1?.carrierName?.toString().orEmpty(),
                    currentDbm = if (slot1 != null) current.sim2.currentDbm else null
                )
            )
        }
    }

    fun startMonitoring(onSignalSample: (slotIndex: Int, dbm: Int) -> Unit) {
        onSignalSampleListener = onSignalSample
        if (!hasPhoneStatePermission()) {
            refreshSubscriptions()
            return
        }

        if (isStarted) {
            registerPerSimCallbacks()
            return
        }
        isStarted = true

        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val subListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                registerPerSimCallbacks()
            }
        }
        subscriptionsChangedListener = subListener
        mainHandler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    subManager?.addOnSubscriptionsChangedListener(context.mainExecutor, subListener)
                } else {
                    @Suppress("DEPRECATION")
                    subManager?.addOnSubscriptionsChangedListener(subListener)
                }
            } catch (_: SecurityException) {
            }
        }

        registerPerSimCallbacks()
    }

    fun stopMonitoring() {
        isStarted = false
        onSignalSampleListener = null
        unregisterAllCallbacks()
        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        subscriptionsChangedListener?.let { listener ->
            try {
                subManager?.removeOnSubscriptionsChangedListener(listener)
            } catch (_: Exception) {
            }
        }
        subscriptionsChangedListener = null
    }

    @SuppressLint("MissingPermission")
    private fun registerPerSimCallbacks() {
        unregisterAllCallbacks()
        refreshSubscriptions()

        if (!hasPhoneStatePermission()) return
        val baseTm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val status = _dualSimStatus.value

        listOf(status.sim1, status.sim2).forEach { slot ->
            if (!slot.isDetected || slot.subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return@forEach
            }
            val subTm = baseTm.createForSubscriptionId(slot.subscriptionId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        val dbm = extractDbm(signalStrength) ?: return
                        updateSlotDbm(slot.slotIndex, dbm)
                    }
                }
                try {
                    subTm.registerTelephonyCallback(context.mainExecutor, callback)
                    activeCallbacksApi31[slot.subscriptionId] = callback
                } catch (_: SecurityException) {
                }
            } else {
                mainHandler.post {
                    @Suppress("DEPRECATION")
                    val listener = object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                            val dbm = signalStrength?.let { extractDbm(it) } ?: return
                            updateSlotDbm(slot.slotIndex, dbm)
                        }
                    }
                    try {
                        @Suppress("DEPRECATION")
                        subTm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                        activeListenersLegacy[slot.subscriptionId] = listener
                    } catch (_: SecurityException) {
                    }
                }
            }
        }
    }

    private fun unregisterAllCallbacks() {
        val baseTm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activeCallbacksApi31.forEach { (subId, callback) ->
                try {
                    baseTm.createForSubscriptionId(subId).unregisterTelephonyCallback(callback)
                } catch (_: Exception) {
                }
            }
            activeCallbacksApi31.clear()
        } else {
            mainHandler.post {
                @Suppress("DEPRECATION")
                activeListenersLegacy.forEach { (subId, listener) ->
                    try {
                        baseTm.createForSubscriptionId(subId)
                            .listen(listener, PhoneStateListener.LISTEN_NONE)
                    } catch (_: Exception) {
                    }
                }
                activeListenersLegacy.clear()
            }
        }
    }

    private fun updateSlotDbm(slotIndex: Int, dbm: Int) {
        _dualSimStatus.update { current ->
            if (slotIndex == 0) {
                current.copy(sim1 = current.sim1.copy(currentDbm = dbm))
            } else {
                current.copy(sim2 = current.sim2.copy(currentDbm = dbm))
            }
        }
        onSignalSampleListener?.invoke(slotIndex, dbm)
    }

    fun latestActiveDbm(): Int? {
        val current = _dualSimStatus.value
        return current.sim1.currentDbm ?: current.sim2.currentDbm
    }

    private fun extractDbm(signalStrength: SignalStrength): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val validDbm = signalStrength.cellSignalStrengths
                .map { it.dbm }
                .firstOrNull { it != CellInfo.UNAVAILABLE && it in -140..-30 }
            if (validDbm != null) return validDbm
        }
        return when (signalStrength.level) {
            4 -> -70
            3 -> -85
            2 -> -100
            1 -> -112
            0 -> -120
            else -> null
        }
    }

    companion object {
        const val COOLDOWN_MILLIS: Long = 15L * 60L * 1000L
    }
}
