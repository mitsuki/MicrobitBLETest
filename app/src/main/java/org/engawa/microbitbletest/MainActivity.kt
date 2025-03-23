package org.engawa.microbitbletest

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import org.engawa.microbitbletest.ui.theme.MicrobitBLETestTheme
import java.nio.charset.StandardCharsets
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        setContent {
            MicrobitBLETestTheme {
                val msg = remember { mutableStateListOf<String>() }
                val devices = remember { mutableStateListOf<BleDevice>() }
                val connect = remember { mutableStateListOf<BleDevice>() }
                val services = remember { mutableStateListOf<BluetoothGattService>() }
                val chars = remember { mutableStateListOf<BluetoothGattCharacteristic>() }
                val data = remember { mutableStateListOf<BluetoothGattCharacteristic>() }
                val recv = remember { mutableStateMapOf<UUID, ByteArray>() }
                Column {
                    Scan(msg, devices, connect, services, chars, data, recv)
                    Devices(msg, devices, connect, services, chars, data, recv)
                    Connect(msg, devices, connect, services, chars, data, recv)
                    Services(msg, devices, connect, services, chars, data, recv)
                    Chars(msg, devices, connect, services, chars, data, recv)
                    Data(msg, devices, connect, services, chars, data, recv)
                    Messages(msg)
                }
            }
        }
    }

    @Composable
    fun Messages(msg: SnapshotStateList<String>)
    {
        val state = rememberLazyListState()
        LaunchedEffect(msg.size)
        {
            state.scrollToItem(msg.size, 0)
        }
        LazyColumn(state = state) {
            items(msg) {
                Text(it)
            }
        }
    }

    data class BleDevice(
        val name: String,
        val address: String,
        val device: BluetoothDevice,
        var gatt: BluetoothGatt? = null
    )

    @Composable
    fun Scan(msg: SnapshotStateList<String>,
             devices: SnapshotStateList<BleDevice>,
             connect: SnapshotStateList<BleDevice>,
             services: SnapshotStateList<BluetoothGattService>,
             chars: SnapshotStateList<BluetoothGattCharacteristic>,
             data: SnapshotStateList<BluetoothGattCharacteristic>,
             recv: SnapshotStateMap<UUID, ByteArray>
    ) {
        val scanning = remember { mutableStateOf(false) }
        val findDeviceName = remember { mutableStateOf( "BBC micro:bit" ) }

        val leScanCallback = remember {
            object : ScanCallback() {
                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    msg.add("onScanFailed($errorCode)")
                }
                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    super.onBatchScanResults(results)
                    msg.add("onBatchScanResults(${results?.size})")
                }
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    result.device?.let { dev ->
                        val name = dev.name ?: ""
                        val address = dev.address ?: ""
                        if (devices.all { it.address != address }) {
                            if (name.startsWith(findDeviceName.value)) {
                                devices.add(BleDevice(name, address, dev))
                            }
                        }
                    }
                }
            }
        }

        TextField(
            label = { Text("Find device name") },
            value = findDeviceName.value,
            enabled = !scanning.value,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = { findDeviceName.value = it }
        )
        Row {
            Button(
                content = { Text("Scan Start") },
                enabled = !scanning.value,
                onClick = {
                    devices.clear()
                    val scanner = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
                    if (scanner != null) {
                        scanning.value = true
                        scanner.startScan(leScanCallback)
                    } else {
                        showToast("No scanner.")
                    }
                }
            )
            Button(
                content = { Text("Stop") },
                enabled = scanning.value,
                onClick = {
                    val scanner = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
                    if (scanner != null) {
                        scanner.stopScan(leScanCallback)
                    } else {
                        showToast("No scanner.")
                    }
                    scanning.value = false
                }
            )
            if (services.isNotEmpty()) {
                Button(
                    content = { Text("Discon.") },
                    onClick = {
                        msg.clear()
                        recv.clear()
                        data.clear()
                        chars.clear()
                        services.clear()
                        if (connect.isNotEmpty()) {
                            connect.first().gatt?.disconnect()
                        }
                    }
                )
            }
            if (chars.isNotEmpty()) {
                Button(
                    content = { Text("Back") },
                    onClick = {
                        msg.clear()
                        recv.clear()
                        data.clear()
                        chars.clear()
                    }
                )
            }
        }
    }

    @Composable
    fun Devices(msg: SnapshotStateList<String>,
                devices: SnapshotStateList<BleDevice>,
                connect: SnapshotStateList<BleDevice>,
                services: SnapshotStateList<BluetoothGattService>,
                chars: SnapshotStateList<BluetoothGattCharacteristic>,
                data: SnapshotStateList<BluetoothGattCharacteristic>,
                recv: SnapshotStateMap<UUID, ByteArray>
    ) {
        if (devices.isNotEmpty())
        {
            LazyColumn {
                items(devices) {
                    if (connect.size == 0 || it.address == connect.first().address)
                    {
                        Button(
                            content = { Text("${it.name} [${it.address}]") },
                            enabled = connect.size == 0,
                            onClick = {
                                msg.clear()
                                connect.clear()
                                connect.add(it)
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun Connect(msg: SnapshotStateList<String>,
                devices: SnapshotStateList<BleDevice>,
                connect: SnapshotStateList<BleDevice>,
                services: SnapshotStateList<BluetoothGattService>,
                chars: SnapshotStateList<BluetoothGattCharacteristic>,
                data: SnapshotStateList<BluetoothGattCharacteristic>,
                recv: SnapshotStateMap<UUID, ByteArray>
    ) {
        val context = LocalContext.current

        val gatCallBack = remember {
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int,newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    msg.add("onConnectionStateChange(${newState.toStateString()})")
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            gatt?.discoverServices()
                        }
                        BluetoothGatt.STATE_DISCONNECTED -> {
                            connect.first().gatt?.close()
                            connect.first().gatt = null
                            connect.clear()
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    msg.add("onServicesDiscovered($status)")
                    if (status == BluetoothGatt.GATT_SUCCESS && gatt?.services?.isNotEmpty() == true) {
                        services.clear()
                        services.addAll(gatt.services)
                    }
                }

                override fun onServiceChanged(gatt: BluetoothGatt) {
                    super.onServiceChanged(gatt)
                    msg.add("onServiceChanged()")
                }

                override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
                    super.onDescriptorRead(gatt, descriptor, status, value)
                    recv[descriptor.uuid] = value
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    super.onCharacteristicRead(gatt, char, value, status)
                    recv[char.uuid] = value
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
                    super.onCharacteristicChanged(gatt, char, value)
                    recv[char.uuid] = value
                }
            }
        }

        LaunchedEffect(connect.size) {
            if (connect.isNotEmpty()) {
                if (connect.first().gatt == null) {
                    val con = connect.first()
                    con.gatt = con.device.connectGatt(context, true, gatCallBack)
                    //device = adapter.getRemoteDevice(con.address)
                }
            }
        }
    }

    @Composable
    fun Services(msg: SnapshotStateList<String>,
                 devices: SnapshotStateList<BleDevice>,
                 connect: SnapshotStateList<BleDevice>,
                 services: SnapshotStateList<BluetoothGattService>,
                 chars: SnapshotStateList<BluetoothGattCharacteristic>,
                 data: SnapshotStateList<BluetoothGattCharacteristic>,
                 recv: SnapshotStateMap<UUID, ByteArray>
    ) {
        if (services.isNotEmpty())
        {
            if (chars.isEmpty()) {
                LazyColumn {
                    items(services) { service ->
                        Button(
                            content = { Text(service.uuid.toBleName()) },
                            onClick = {
                                msg.clear()
                                chars.clear()
                                chars.addAll(service.characteristics)
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun Chars(msg: SnapshotStateList<String>,
              devices: SnapshotStateList<BleDevice>,
              connect: SnapshotStateList<BleDevice>,
              services: SnapshotStateList<BluetoothGattService>,
              chars: SnapshotStateList<BluetoothGattCharacteristic>,
              data: SnapshotStateList<BluetoothGattCharacteristic>,
              recv: SnapshotStateMap<UUID, ByteArray>
    ) {
        if (chars.isNotEmpty()) {
            LazyColumn {
                items(chars) { char ->
                    Button(
                        content = { Text(char.uuid.toBleName()) },
                        onClick = {
                            msg.clear()
                            recv.clear()
                            data.clear()
                            data.add(char)
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun Data(msg: SnapshotStateList<String>,
             devices: SnapshotStateList<BleDevice>,
             connect: SnapshotStateList<BleDevice>,
             services: SnapshotStateList<BluetoothGattService>,
             chars: SnapshotStateList<BluetoothGattCharacteristic>,
             data: SnapshotStateList<BluetoothGattCharacteristic>,
             recv: SnapshotStateMap<UUID, ByteArray>
    ) {
        if (data.isNotEmpty()) {
            val char = data.first()
            Text("Service: ${char.service.uuid.toBleName()}")
            Text("Type: ${"0x%02X".format(char.service.type)} ${if (char.service.type == 0) "PRIMARY" else "SECONDARY"}")
            Text("Char.: ${char.uuid.toBleName()}")
            Text("Permissions: ${"0x%02X".format(char.permissions)} ${char.permissions.toPermissionString()}")
            Text("Properties: ${"0x%02X".format(char.properties)} ${char.properties.toPropertyString()}")
            Text("WriteType: ${"0x%02X".format(char.writeType)} ${char.writeType.toWriteTypeString()}")
            char.descriptors.forEach { desc ->
                Text("Desc.: ${desc.uuid.toBleName()}")
                Text("Permissions: ${desc.permissions} ${desc.permissions.toPermissionString()}")
                //connect.first().gatt?.readDescriptor(desc)
            }
            Row {
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                    Button(
                        content = { Text("Read") },
                        onClick = {
                            msg.clear()
                            connect.first().gatt?.readCharacteristic(char)
                        }
                    )
                }
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                    Button(
                        content = { Text("Enable Notify") },
                        onClick = {
                            msg.clear()
                            connect.first().gatt?.setCharacteristicNotification(char, true)
                            val desc = char.getDescriptor(UUID_CLIENT_CHAR_CONFIG)
                            connect.first().gatt?.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        }
                    )
                    Button(
                        content = { Text("Disable Notify") },
                        onClick = {
                            msg.clear()
                            connect.first().gatt?.setCharacteristicNotification(char, false)
                            val desc = char.getDescriptor(UUID_CLIENT_CHAR_CONFIG)
                            connect.first().gatt?.writeDescriptor(desc, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                        }
                    )
                }
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
                    Button(
                        content = { Text("Enable Indicate") },
                        onClick = {
                            msg.clear()
                            connect.first().gatt?.setCharacteristicNotification(char, true)
                            val desc = char.getDescriptor(UUID_CLIENT_CHAR_CONFIG)
                            connect.first().gatt?.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        }
                    )
                    Button(
                        content = { Text("Disable Indicate") },
                        onClick = {
                            msg.clear()
                            connect.first().gatt?.setCharacteristicNotification(char, false)
                            val desc = char.getDescriptor(UUID_CLIENT_CHAR_CONFIG)
                            connect.first().gatt?.writeDescriptor(desc, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                        }
                    )
                }
            }
            recv.forEach {
                Text("Char.: ${it.key.toBleName()}")
                Text("Hex: ${it.value.toHexDump()}")
                Text("Text: ${it.value.toString(StandardCharsets.UTF_8)}")
            }
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0 ||
                char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE > 0) {
                val writeData = remember { mutableStateOf( "" ) }

                Row {
                    fun write(a: ByteArray) {
                        msg.clear()
                        connect.first().gatt?.writeCharacteristic(char, a, char.writeType)
                    }
                    Button(
                        content = { Text("Hex") },
                        onClick = {
                            try {
                                write(writeData.value.split(Regex(" +")).map {
                                    it.toUByte(16).toByte()
                                }.toByteArray())
                            } catch (e: Exception) {
                                showToast(e.message ?: "error.")
                            }
                        }
                    )
                    Button(
                        content = { Text("Bin") },
                        onClick = {
                            try {
                                write(writeData.value.split(Regex(" +")).map {
                                    it.toUByte(2).toByte()
                                }.toByteArray())
                            } catch (e: Exception) {
                                showToast(e.message ?: "error.")
                            }
                        }
                    )
                    Button(
                        content = { Text("Str") },
                        onClick = {
                            write(writeData.value.toByteArray())
                        }
                    )
                    Button(
                        content = { Text("LF") },
                        onClick = {
                            write("\n".toByteArray())
                        }
                    )
                }
                TextField(
                    label = { Text("Write data") },
                    value = writeData.value,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { writeData.value = it }
                )
            }
        }
    }

    private fun ByteArray.toHexDump(): String {
        return this.joinToString(" ") { "%02X".format(it) }
    }

    private fun Int.toStateString(): String {
        return when (this) {
            BluetoothGatt.STATE_DISCONNECTED                        -> "DISCONNECTED"
            BluetoothGatt.STATE_CONNECTING                          -> "CONNECTING"
            BluetoothGatt.STATE_CONNECTED                           -> "CONNECTED"
            BluetoothGatt.STATE_DISCONNECTING                       -> "DISCONNECTING"
            else                                                    -> "UNKNOWN"
        }
    }

    private val mapOfPermissions = mapOf( // for characteristic and descriptor
        BluetoothGattCharacteristic.PERMISSION_READ                 to "READ",
        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED       to "READ(E)",
        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM  to "READ(EM)",
        BluetoothGattCharacteristic.PERMISSION_WRITE                to "WRITE",
        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED      to "WRITE(E)",
        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM to "WRITE(EM)",
        BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED         to "WRITE(S)",
        BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM    to "WRITE(SM)",
    )
    private val mapOfProperties = mapOf(
        BluetoothGattCharacteristic.PROPERTY_BROADCAST              to "BROADCAST",
        BluetoothGattCharacteristic.PROPERTY_READ                   to "READ",
        BluetoothGattCharacteristic.PROPERTY_WRITE                  to "WRITE",
        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE      to "WRITE(NR)",
        BluetoothGattCharacteristic.PROPERTY_NOTIFY                 to "NOTIFY",
        BluetoothGattCharacteristic.PROPERTY_INDICATE               to "INDICATE",
        BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE           to "WRITE(S)",
        BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS         to "EXT",
    )
    private val mapOfWriteTypes = mapOf(
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE          to "NO_RESPONSE",
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT              to "DEFAULT",
        BluetoothGattCharacteristic.WRITE_TYPE_SIGNED               to "SIGNED",
    )
    private fun toBitString(v:Int, t: Map<Int,String>): String {
        return t.mapNotNull { if (v and it.key > 0) it.value else null }.joinToString("/")
    }
    private fun Int.toPermissionString(): String { return toBitString(this, mapOfPermissions) }
    private fun Int.toPropertyString(): String { return toBitString(this, mapOfProperties) }
    private fun Int.toWriteTypeString(): String { return toBitString(this, mapOfWriteTypes) }

    private val UUID_CLIENT_CHAR_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun UUID.toBleName(): String {
        val key = this.toString().toUpperCase(Locale.current).replace("-", "")
        return mapOfBleNames.getOrDefault(key, key)
    }
    private val mapOfBleNames = mapOf(
        // BLE Services
        "0000180000001000800000805F9B34FB" to "1800:Generic Access",
        "0000180100001000800000805F9B34FB" to "1801:Generic Attribute",
        "0000180200001000800000805F9B34FB" to "1802:Immediate Alert Service",
        "0000180300001000800000805F9B34FB" to "1803:Link Loss Service",
        "0000180400001000800000805F9B34FB" to "1804:Tx Power Service",
        "0000180500001000800000805F9B34FB" to "1805:Current Time Service",
        "0000180600001000800000805F9B34FB" to "1806:Reference Time Update Service",
        "0000180700001000800000805F9B34FB" to "1807:Next DST Change Service",
        "0000180800001000800000805F9B34FB" to "1808:Glucose Service",
        "0000180900001000800000805F9B34FB" to "1809:Health Thermometer Service",
        "0000180A00001000800000805F9B34FB" to "180A:Device Information",
        "0000180D00001000800000805F9B34FB" to "180D:Heart Rate Service",
        "0000180E00001000800000805F9B34FB" to "180E:Phone Alert Status Service",
        "0000180F00001000800000805F9B34FB" to "180F:Battery Service",
        "0000181000001000800000805F9B34FB" to "1810:Blood Pressure Service",
        "0000181100001000800000805F9B34FB" to "1811:Alert Notification Service",
        "0000181200001000800000805F9B34FB" to "1812:Human Interface Device Service",
        "0000181300001000800000805F9B34FB" to "1813:Scan Parameters Service",
        "0000181400001000800000805F9B34FB" to "1814:Running Speed and Cadence Service",
        "0000181500001000800000805F9B34FB" to "1815:Automation IO Service",
        "0000181600001000800000805F9B34FB" to "1816:Cycling Speed and Cadence Service",
        "0000181800001000800000805F9B34FB" to "1818:Cycling Power Service",
        "0000181900001000800000805F9B34FB" to "1819:Location and Navigation Service",
        "0000181A00001000800000805F9B34FB" to "181A:Environmental Sensing Service",
        "0000181B00001000800000805F9B34FB" to "181B:Body Composition Service",
        "0000181C00001000800000805F9B34FB" to "181C:User Data Service",
        "0000181D00001000800000805F9B34FB" to "181D:Weight Scale Service",
        "0000181E00001000800000805F9B34FB" to "181E:Bond Management Service",
        "0000181F00001000800000805F9B34FB" to "181F:Continuous Glucose Monitoring Service",
        "0000182000001000800000805F9B34FB" to "1820:Internet Protocol Support Service",
        "0000182100001000800000805F9B34FB" to "1821:Indoor Positioning Service",
        "0000182200001000800000805F9B34FB" to "1822:Pulse Oximeter Service",
        "0000182300001000800000805F9B34FB" to "1823:HTTP Proxy Service",
        "0000182400001000800000805F9B34FB" to "1824:Transport Discovery Service",
        "0000182500001000800000805F9B34FB" to "1825:Object Transfer Service",
        "0000182600001000800000805F9B34FB" to "1826:Fitness Machine Service",
        "0000182700001000800000805F9B34FB" to "1827:Mesh Provisioning Service",
        "0000182800001000800000805F9B34FB" to "1828:Mesh Proxy Service",
        "0000182900001000800000805F9B34FB" to "1829:Reconnection Configuration Service",
        "0000183A00001000800000805F9B34FB" to "183A:Insulin Delivery Service",
        "0000183B00001000800000805F9B34FB" to "183B:Binary Sensor Service",
        "0000183C00001000800000805F9B34FB" to "183C:Emergency Configuration Service",
        "0000183D00001000800000805F9B34FB" to "183D:Authorization Control Service",
        "0000183E00001000800000805F9B34FB" to "183E:Physical Activity Monitor Service",
        "0000183F00001000800000805F9B34FB" to "183F:Elapsed Time Service",
        "0000184000001000800000805F9B34FB" to "1840:Generic Health Sensor Service",
        "0000184300001000800000805F9B34FB" to "1843:Audio Input Control Service",
        "0000184400001000800000805F9B34FB" to "1844:Volume Control Service",
        "0000184500001000800000805F9B34FB" to "1845:Volume Offset Control Service",
        "0000184600001000800000805F9B34FB" to "1846:Coordinated Set Identification Service",
        "0000184700001000800000805F9B34FB" to "1847:Device Time Service",
        "0000184800001000800000805F9B34FB" to "1848:Media Control Service",
        "0000184900001000800000805F9B34FB" to "1849:Generic Media Control Service",
        "0000184A00001000800000805F9B34FB" to "184A:Constant Tone Extension Service",
        "0000184B00001000800000805F9B34FB" to "184B:Telephone Bearer Service",
        "0000184C00001000800000805F9B34FB" to "184C:Generic Telephone Bearer Service",
        "0000184D00001000800000805F9B34FB" to "184D:Microphone Control Service",
        "0000184E00001000800000805F9B34FB" to "184E:Audio Stream Control Service",
        "0000184F00001000800000805F9B34FB" to "184F:Broadcast Audio Scan Service",
        "0000185000001000800000805F9B34FB" to "1850:Published Audio Capabilities Service",
        "0000185100001000800000805F9B34FB" to "1851:Basic Audio Announcement Service",
        "0000185200001000800000805F9B34FB" to "1852:Broadcast Audio Announcement Service",
        "0000185300001000800000805F9B34FB" to "1853:Common Audio Service",
        "0000185400001000800000805F9B34FB" to "1854:Hearing Access Service",
        "0000185500001000800000805F9B34FB" to "1855:Telephony and Media Audio Service",
        "0000185600001000800000805F9B34FB" to "1856:Public Broadcast Announcement Service",
        "0000185700001000800000805F9B34FB" to "1857:Electronic Shelf Label Service",
        "0000185800001000800000805F9B34FB" to "1858:Gaming Audio Service",
        "0000185900001000800000805F9B34FB" to "1859:Mesh Proxy Solicitation Service",
        "0000185A00001000800000805F9B34FB" to "185A:Industrial Measurement Device Service",
        "0000185B00001000800000805F9B34FB" to "185B:Ranging Service",
        "0000FE5900001000800000805F9B34FB" to "FE59:DFU OTA primary service?",
        // BLE Characteristics
        "00002A0000001000800000805F9B34FB" to "2A00:Device Name",
        "00002A0100001000800000805F9B34FB" to "2A01:Appearance",
        "00002A0200001000800000805F9B34FB" to "2A02:Peripheral Privacy Flag",
        "00002A0300001000800000805F9B34FB" to "2A03:Reconnection Address",
        "00002A0400001000800000805F9B34FB" to "2A04:Peripheral Preferred Connection Parameters",
        "00002A0500001000800000805F9B34FB" to "2A05:Service Changed",
        "00002A2400001000800000805F9B34FB" to "2A24:Model Number String",
        "00002A2500001000800000805F9B34FB" to "2A25:Serial Number String",
        "00002A2600001000800000805F9B34FB" to "2A26:Firmware Revision String",
        "00002A2700001000800000805F9B34FB" to "2A27:Hardware Revision String",
        "00002A2900001000800000805F9B34FB" to "2A29:Manufacturer Name String",
        "00002A3700001000800000805F9B34FB" to "2A37:Heart Rate Measurement",
        "00002A3800001000800000805F9B34FB" to "2A38:Body Sensor Location",
        "00002AA600001000800000805F9B34FB" to "2AA6:Central Address Resolution",
        // BLE Descriptors
        "0000290000001000800000805F9B34FB" to "2900:Characteristic Extended Properties",
        "0000290100001000800000805F9B34FB" to "2901:Characteristic User Description Descriptor",
        "0000290200001000800000805F9B34FB" to "2902:Client Characteristic Configuration",
        "0000290300001000800000805F9B34FB" to "2903:Server Characteristic Configuration",
        "0000290400001000800000805F9B34FB" to "2904:Server Characteristic Format",
        "0000290500001000800000805F9B34FB" to "2905:Characteristic Aggregate Format",
        "0000290600001000800000805F9B34FB" to "2906:Valid Range",
        "0000290700001000800000805F9B34FB" to "2907:External Report Reference",
        "0000290800001000800000805F9B34FB" to "2908:Report Reference",
        "0000290900001000800000805F9B34FB" to "2909:Number of Digitals",
        "0000290A00001000800000805F9B34FB" to "290A:Value Trigger Setting",
        "0000290B00001000800000805F9B34FB" to "290B:Environmental Sensing Configuration",
        "0000290C00001000800000805F9B34FB" to "290C:Environmental Sensing Measurement",
        "0000290D00001000800000805F9B34FB" to "290D:Environmental Sensing Trigger Setting",
        "0000290E00001000800000805F9B34FB" to "290E:Time Trigger Setting",
        "0000290F00001000800000805F9B34FB" to "290F:Complete BR-EDR Transport Block Data",
        "0000291000001000800000805F9B34FB" to "2910:Observation Schedule",
        "0000291100001000800000805F9B34FB" to "2911:Valid Range and Accuracy",
        "0000291200001000800000805F9B34FB" to "2912:Measurement Description",
        "0000291300001000800000805F9B34FB" to "2913:Manufacturer Limits",
        "0000291400001000800000805F9B34FB" to "2914:Process Tolerances",
        "0000291500001000800000805F9B34FB" to "2915:IMD Trigger Setting",
        // micro:bit
        "E95D0753251D470AA062FA1922DFA9A8" to "ACCELEROMETER SERVICE",
        "E95DCA4B251D470AA062FA1922DFA9A8" to "Accelerometer Data",
        "E95DFB24251D470AA062FA1922DFA9A8" to "Accelerometer Period",
        "E95DF2D8251D470AA062FA1922DFA9A8" to "MAGNETOMETER SERVICE",
        "E95DFB11251D470AA062FA1922DFA9A8" to "Magnetometer Data",
        "E95D386C251D470AA062FA1922DFA9A8" to "Magnetometer Period",
        "E95D9715251D470AA062FA1922DFA9A8" to "Magnetometer Bearing",
        "E95DB358251D470AA062FA1922DFA9A8" to "Magnetometer Calibration",
        "E95D9882251D470AA062FA1922DFA9A8" to "BUTTON SERVICE",
        "E95DDA90251D470AA062FA1922DFA9A8" to "Button A State",
        "E95DDA91251D470AA062FA1922DFA9A8" to "Button B State",
        "E95D127B251D470AA062FA1922DFA9A8" to "IO PIN SERVICE",
        "E95D8D00251D470AA062FA1922DFA9A8" to "Pin Data",
        "E95D5899251D470AA062FA1922DFA9A8" to "Pin AD Configuration",
        "E95DB9FE251D470AA062FA1922DFA9A8" to "Pin IO Configuration",
        "E95DD822251D470AA062FA1922DFA9A8" to "PWM Control",
        "E95DD91D251D470AA062FA1922DFA9A8" to "LED SERVICE",
        "E95D7B77251D470AA062FA1922DFA9A8" to "LED Matrix State",
        "E95D93EE251D470AA062FA1922DFA9A8" to "LED Text",
        "E95D0D2D251D470AA062FA1922DFA9A8" to "Scrolling Delay",
        "E95D93AF251D470AA062FA1922DFA9A8" to "EVENT SERVICE",
        "E95DB84C251D470AA062FA1922DFA9A8" to "MicroBit Requirements",
        "E95D9775251D470AA062FA1922DFA9A8" to "MicroBit Event",
        "E95D23C4251D470AA062FA1922DFA9A8" to "Client Requirements",
        "E95D5404251D470AA062FA1922DFA9A8" to "Client Event",
        "E95D93B0251D470AA062FA1922DFA9A8" to "DFU CONTROL SERVICE",
        "E95D93B1251D470AA062FA1922DFA9A8" to "DFU Control",
        "E95D6100251D470AA062FA1922DFA9A8" to "TEMPERATURE SERVICE",
        "E95D9250251D470AA062FA1922DFA9A8" to "Temperature",
        "E95D1B25251D470AA062FA1922DFA9A8" to "Temperature Period",
        "6E400001B5A3F393E0A9E50E24DCCA9E" to "UART SERVICE",
        "6E400002B5A3F393E0A9E50E24DCCA9E" to "TX Characteristic",
        "6E400003B5A3F393E0A9E50E24DCCA9E" to "RX Characteristic",
    )
}

fun ComponentActivity.showToast(message:String)
{
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

