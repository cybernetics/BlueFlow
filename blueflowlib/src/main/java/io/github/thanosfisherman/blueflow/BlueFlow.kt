package io.github.thanosfisherman.blueflow

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.text.TextUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow

class BlueFlow(private val context: Context) {

    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    /**
     * Return true if Bluetooth is available.
     *
     * @return true if bluetoothAdapter is not null or it's address is empty, otherwise Bluetooth is
     * not supported on this hardware platform
     */
    fun isBluetoothAvailable() =
        !(bluetoothAdapter == null || TextUtils.isEmpty(bluetoothAdapter.address))

    /**
     * Return true if Bluetooth is currently enabled and ready for use.
     * <p>Equivalent to:
     * <code>getBluetoothState() == STATE_ON</code>
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return true if the local adapter is turned on
     */
    fun isBluetoothEnabled() = bluetoothAdapter.isEnabled

    /**
     * Return true if a location service is enabled.
     *
     * @return true if either the GPS or Network provider is enabled
     */
    fun isLocationServiceEnabled(): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    /**
     * This will issue a request to enable Bluetooth through the system settings (without stopping
     * your application) via ACTION_REQUEST_ENABLE action Intent.
     *
     * @param activity Activity
     * @param requestCode request code
     */
    fun enableBluetooth(activity: Activity, requestCode: Int) {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent, requestCode)
        }
    }

    /**
     * Turn on the local Bluetooth adapter — do not use without explicit user action to turn on
     * Bluetooth.
     *
     * @return true to indicate adapter startup has begun, or false on
     * immediate error
     * @see BluetoothAdapter.enable
     */
    fun enable() = bluetoothAdapter.enable()


    /**
     * Turn off the local Bluetooth adapter — do not use without explicit user action to turn off
     * Bluetooth.
     *
     * @return true to indicate adapter shutdown has begun, or false on
     * immediate error
     * @see BluetoothAdapter.enable
     */
    fun disable() = bluetoothAdapter.disable()

    /**
     * Return the set of [BluetoothDevice] objects that are bonded
     * (paired) to the local adapter.
     *
     * If Bluetooth state is not [BluetoothAdapter.STATE_ON], this API
     * will return an empty set. After turning on Bluetooth,
     * wait for [BluetoothAdapter.ACTION_STATE_CHANGED] with [BluetoothAdapter.STATE_ON]
     * to get the updated value.
     *
     * Requires [android.Manifest.permission.BLUETOOTH].
     *
     * @return unmodifiable set of [BluetoothDevice], or null on error
     */
    fun bondedDevices(): Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices

    /**
     * Start the remote device discovery process.
     *
     * @return true on success, false on error
     */
    fun startDiscovery() = bluetoothAdapter.startDiscovery()

    /**
     * Return true if the local Bluetooth adapter is currently in the device
     * discovery process.
     *
     * @return true if discovering
     */
    fun isDiscovering() = bluetoothAdapter.isDiscovering

    /**
     * Cancel the current device discovery process.
     *
     * @return true on success, false on error
     */
    fun cancelDiscovery() = bluetoothAdapter.cancelDiscovery()

    /**
     * This will issue a request to make the local device discoverable to other devices. By default,
     * the device will become discoverable for 120 seconds.
     *
     * @param activity Activity
     * @param requestCode request code
     */
    fun enableDiscoverability(activity: Activity, requestCode: Int) {
        enableDiscoverability(activity, requestCode, -1)
    }

    /**
     * This will issue a request to make the local device discoverable to other devices. By default,
     * the device will become discoverable for 120 seconds.  Maximum duration is capped at 300
     * seconds.
     *
     * @param activity Activity
     * @param requestCode request code
     * @param duration discoverability duration in seconds
     */
    fun enableDiscoverability(activity: Activity, requestCode: Int, duration: Int) {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        if (duration >= 0) {
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration)
        }
        activity.startActivityForResult(discoverableIntent, requestCode)
    }

    /**
     * Observes Bluetooth devices found while discovering.
     *
     * @return Flow Observable with BluetoothDevice found
     */
    @ExperimentalCoroutinesApi
    fun discoverDevices() = channelFlow {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                    val device =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice
                    offer(device)
                }
            }
        }
        context.registerReceiver(receiver, filter)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Observes DiscoveryState, which can be ACTION_DISCOVERY_STARTED or ACTION_DISCOVERY_FINISHED
     * from {@link BluetoothAdapter}.
     *
     * @return Flow Observable with DiscoveryState
     */
    @ExperimentalCoroutinesApi
    fun discoveryState() = channelFlow {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { offer(it.action) }
            }
        }
        context.registerReceiver(receiver, filter)
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Observes BluetoothState. Possible values are:
     * {@link BluetoothAdapter#STATE_OFF},
     * {@link BluetoothAdapter#STATE_TURNING_ON},
     * {@link BluetoothAdapter#STATE_ON},
     * {@link BluetoothAdapter#STATE_TURNING_OFF},
     *
     * @return Flow Observable with BluetoothState
     */
    @ExperimentalCoroutinesApi
    fun bluetoothState() = channelFlow {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                offer(bluetoothAdapter.state)
            }
        }
        context.registerReceiver(receiver, filter)
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Observes scan mode of device. Possible values are:
     * {@link BluetoothAdapter#SCAN_MODE_NONE},
     * {@link BluetoothAdapter#SCAN_MODE_CONNECTABLE},
     * {@link BluetoothAdapter#SCAN_MODE_CONNECTABLE_DISCOVERABLE}
     *
     * @return Flow Observable with scan mode
     */
    @ExperimentalCoroutinesApi
    fun scanMode() = channelFlow {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                offer(bluetoothAdapter.scanMode)
            }
        }
        context.registerReceiver(receiver, filter)
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    fun bluetoothProfile(bluetoothProfile: Int): Flow<ServiceEvent> = flow {
       val listener = object : BluetoothProfile.ServiceListener {
           override fun onServiceDisconnected(profile: Int) {
               TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
           }

           override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
               TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
           }

       }

    }
}