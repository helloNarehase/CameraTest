package com.nare.cameratest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.graphics.Color.Companion.Cyan
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.nare.cameratest.ui.theme.CameraTestTheme
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


const val TAG = "Gahi"

lateinit var imageCapture:ImageCapture
var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
lateinit var previewView:PreviewView
lateinit var handLandmarkerHelper: HandLandmarkerHelper
lateinit var lifecycleOwner:LifecycleOwner
lateinit var backgroundExecutor:ExecutorService
var toast: Toast? = null
//lateinit var outputOptions:ImageCapture.OutputFileOptions
data class Marker(var x:Double = 0.0, var y:Double = 0.0, var z:Double = 0.0)
class MainActivity : ComponentActivity(), HandLandmarkerHelper.LandmarkerListener {
    lateinit var mBtAdapter: BluetoothManager
    var toggle = false
    private var mBtDevice: BluetoothDevice? = null
        private var mBtHidDevice: BluetoothHidDevice? = null
    private var mBluetoothHidDeviceAppQosSettings: BluetoothHidDeviceAppQosSettings? = null
    private val makers:Marker = Marker()
    private val subMakers:Marker = Marker()
    private var accX:Float = 0f
    private var accY:Float = 0f

    lateinit var localView:View
    private val cameraIndex = mutableStateOf(1)
    private val states = mutableStateOf("")
    private val makerBias = mutableStateOf(true)
    private val viewModel: MainViewModel = MainViewModel()
    private var result: HandLandmarkerHelper.ResultBundle? = null
    val res: MutableState<HandLandmarkerHelper.ResultBundle> = mutableStateOf(HandLandmarkerHelper.ResultBundle(
        listOf(),
        0L,
        0,
        0
    ))
    val names: MutableState<ArrayList<String>> = mutableStateOf( arrayListOf())
    val mDevices: MutableState<ArrayList<BluetoothDevice?>> = mutableStateOf( arrayListOf())

    val ID_KEYBOARD: Byte = 1
    val y = mutableStateOf(0f)
    val x = mutableStateOf(0f)

    private val permissionList = listOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.BLUETOOTH_ADMIN,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    val REPORT_MAP_KEYBOARD =
            byteArrayOf(
            0x05.toByte(),
            0x07.toByte(),
            0x15.toByte(),
            0x00.toByte(),
            0x25.toByte(),
            0x01.toByte(),
            0x75.toByte(),
            0x01.toByte(),
            0x95.toByte(),
            0x08.toByte(),
            0x09.toByte(),
            0xE0.toByte(),
            0x09.toByte(),
            0xE1.toByte(),
            0x09.toByte(),
            0xE2.toByte(),
            0x09.toByte(),
            0xE3.toByte(),
            0x09.toByte(),
            0xE4.toByte(),
            0x09.toByte(),
            0xE5.toByte(),
            0x09.toByte(),
            0xE6.toByte(),
            0x09.toByte(),
            0xE7.toByte(),
            0x81.toByte(),
            0x02.toByte(),
            0x05.toByte(),
            0x07.toByte(),
            0x95.toByte(),
            0x01.toByte(),
            0x75.toByte(),
            0x08.toByte(),
            0x15.toByte(),
            0x04.toByte(),
            0x25.toByte(),
            0xDF.toByte(),
            0x05.toByte(),
            0x07.toByte(),
            0x19.toByte(),
            0x04.toByte(),
            0x29.toByte(),
            0xDF.toByte(),
            0x81.toByte(),
            0x00.toByte()
        )


    private val descriptors = byteArrayOf(
        // HID descriptor
        0x09, // bLength
        0x21, // bDescriptorType
        0x11, 0x01, // bcdHID
        0x00, // bCountryCode
        0x01, // bNumDescriptors
        0x22, // bDescriptorType
        0x30, 0x00, // wDescriptorLength (48 in decimal)

        // Report descriptor - 4 buttons, 1 X/Y joystick
        0x05, 0x01,        // USAGE_PAGE (Generic Desktop)
        0x09, 0x05,        // USAGE (Game Pad)
        0xa1.toByte(), 0x01, // COLLECTION (Application)
        0xa1.toByte(), 0x00, //   COLLECTION (Physical)
        0x05, 0x09,        //     USAGE_PAGE (Button)
        0x19, 0x01,        //     USAGE_MINIMUM (Button 1)
        0x29, 0x04,        //     USAGE_MAXIMUM (Button 4)
        0x15, 0x00,        //     LOGICAL_MINIMUM (0)
        0x25, 0x01,        //     LOGICAL_MAXIMUM (1)
        0x75, 0x01,        //     REPORT_SIZE (1)
        0x95.toByte(), 0x04, //     REPORT_COUNT (4)
        0x81.toByte(), 0x02, //     INPUT (Data,Var,Abs)
        0x75, 0x04,        //     REPORT_SIZE (4)
        0x95.toByte(), 0x01, //     REPORT_COUNT (1)
        0x81.toByte(), 0x03, //     INPUT (Cnst,Var,Abs)
        0x05, 0x01,        //     USAGE_PAGE (Generic Desktop)
        0x09, 0x30,        //     USAGE (X)
        0x09, 0x31,        //     USAGE (Y)
        0x15, 0x81.toByte(), //     LOGICAL_MINIMUM (-127)
        0x25, 0x7f,        //     LOGICAL_MAXIMUM (127)
        0x75, 0x08,        //     REPORT_SIZE (8)
        0x95.toByte(), 0x02, //     REPORT_COUNT (2)
        0x81.toByte(), 0x02, //     INPUT (Data,Var,Abs)
        0xc0.toByte(),       //   END_COLLECTION
        0xc0.toByte()        // END_COLLECTION
    )
    val des = byteArrayOf(
        0x05, 0x01,                    // USAGE_PAGE (Generic Desktop)  // 47
        0x09, 0x06,                    // USAGE (Keyboard)
        0xa1.toByte(), 0x01,                    // COLLECTION (Application)
        0x85.toByte(), 0x02,                    //   REPORT_ID (2)
        0x05, 0x07,                    //   USAGE_PAGE (Keyboard)

        0x19, 0xe0.toByte(),                    //   USAGE_MINIMUM (Keyboard LeftControl)
        0x29, 0xe7.toByte(),                    //   USAGE_MAXIMUM (Keyboard Right GUI)
        0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
        0x25, 0x01,                    //   LOGICAL_MAXIMUM (1)
        0x75, 0x01,                    //   REPORT_SIZE (1)

        0x95.toByte(), 0x08,                    //   REPORT_COUNT (8)
        0x81.toByte(), 0x02,                    //   INPUT (Data,Var,Abs)
        0x95.toByte(), 0x01,                    //   REPORT_COUNT (1)
        0x75, 0x08,                    //   REPORT_SIZE (8)
        0x81.toByte(), 0x03,                    //   INPUT (Cnst,Var,Abs)

        0x95.toByte(), 0x06,                    //   REPORT_COUNT (6)
        0x75, 0x08,                    //   REPORT_SIZE (8)
        0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
        0x25, 0x73,                    //   LOGICAL_MAXIMUM (115)
        0x05, 0x07,                    //   USAGE_PAGE (Keyboard)

        0x19, 0x00,                    //   USAGE_MINIMUM (Reserved (no event indicated))
        0x29, 0x73,                    //   USAGE_MAXIMUM (Keyboard Application)
        0x81.toByte(), 0x00,                    //   INPUT (Data,Ary,Abs)
        0xc0.toByte(),                          // END_COLLECTION

    )

    private val descriptor = byteArrayOf( // HID descriptor 0x05, 0x01,        // Usage Page (Generic Desktop Ctrls)
        0x09, 0x06,        // Usage (Keyboard)
        0xA1.toByte(), 0x01,        // Collection (Application)
        0x85.toByte(), 0x01,        //   Report ID (1)
        0x05, 0x07,        //   Usage Page (Kbrd/Keypad)
        0x75, 0x01,        //   Report Size (1)
        0x95.toByte(), 0x08,        //   Report Count (8)
        0x19, 0xE0.toByte(),        //   Usage Minimum (0xE0)
        0x29, 0xE7.toByte(),        //   Usage Maximum (0xE7)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x81.toByte(), 0x02,        //   Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
        0x95.toByte(), 0x06,        //   Report Count (6)
        0x75, 0x08,        //   Report Size (8)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x7f,        //   Logical Maximum (127)
        0x05, 0x07,        //   Usage Page (Kbrd/Keypad)
        0x19, 0x00,        //   Usage Minimum (0x00)
        0x29, 0x7f,        //   Usage Maximum (0x7f)
        0x81.toByte(), 0x00,        //   Input (Data,Array,Abs,No Wrap,Linear,Preferred State,No Null Position)
        0xC0.toByte(),              // End Collection
        0x05, 0x0C,        // Usage Page (Consumer)
        0x09, 0x01,        // Usage (Consumer Control)
        0xA1.toByte(), 0x01,        // Collection (Application)
        0x85.toByte(), 0x02,        //   Report ID (2)
        0x05, 0x0C,        //   Usage Page (Consumer)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x75, 0x01,        //   Report Size (1)
        0x95.toByte(), 0x08,        //   Report Count (8)
        0x09, 0xB5.toByte(),        //   Usage (Scan Next Track)
        0x09, 0xB6.toByte(),        //   Usage (Scan Previous Track)
        0x09, 0xB7.toByte(),        //   Usage (Stop)
        0x09, 0xB8.toByte(),        //   Usage (Eject)
        0x09, 0xCD.toByte(),        //   Usage (Play/Pause)
        0x09, 0xE2.toByte(),        //   Usage (Mute)
        0x09, 0xE9.toByte(),        //   Usage (Volume Increment)
        0x09, 0xEA.toByte(),        //   Usage (Volume Decrement)
        0x81.toByte(), 0x02,        //   Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
        0xC0.toByte(),              // End Collection

        0x05, 0x01,                    // USAGE_PAGE (Generic Desktop)
        0x09, 0x02,                    // USAGE (Mouse)
        0xa1.toByte(), 0x01,                    // COLLECTION (Application)
        0x85.toByte(), 0x03,                    //   Report ID (3)
        0x09, 0x01,                    //   USAGE (Pointer)
        0xa1.toByte(), 0x00,                    //   COLLECTION (Physical)
        0x05, 0x09,                    //     USAGE_PAGE (Button)
        0x19, 0x01,                    //     USAGE_MINIMUM (Button 1)
        0x29, 0x03,                    //     USAGE_MAXIMUM (Button 3)
        0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
        0x25, 0x01,                    //     LOGICAL_MAXIMUM (1)
        0x95.toByte(), 0x03,                    //     REPORT_COUNT (3)
        0x75, 0x01,                    //     REPORT_SIZE (1)
        0x81.toByte(), 0x02,                    //     INPUT (Data,Var,Abs)
        0x95.toByte(), 0x01,                    //     REPORT_COUNT (1)
        0x75, 0x05,                    //     REPORT_SIZE (5)
        0x81.toByte(), 0x03,                    //     INPUT (Cnst,Var,Abs)
        0x05, 0x01,                    //     USAGE_PAGE (Generic Desktop)
        0x09, 0x30,                    //     USAGE (X)
        0x09, 0x31,                    //     USAGE (Y)
        0x15, 0x81.toByte(),                    //     LOGICAL_MINIMUM (-127)
        0x25, 0x7f,                    //     LOGICAL_MAXIMUM (127)
        0x75, 0x08,                    //     REPORT_SIZE (8)
        0x95.toByte(), 0x02,                    //     REPORT_COUNT (2)
        0x81.toByte(), 0x06,                    //     INPUT (Data,Var,Rel)
        0xc0.toByte(),                          //   END_COLLECTION
        0xc0.toByte(),                          // END_COLLECTION

        // 69 bytes
        0x05, 0x01,                     // Usage Page (Generic Desktop)
        0x09, 0x05,                     // Usage (Game Pad)
        0xa1.toByte(), 0x01,                     // Collection (Application)
        0x85.toByte(), 0x04,                     //   Report ID (4)

        0x05, 0x01,                     //     Usage Page (Generic Desktop)
        0x09, 0x00,                     //     Usage (Undefined)
        0x75, 0x08,                     //     Report Size (8)
        0x95.toByte(), 0x01,                     //     Report Count (1)
        0x81.toByte(), 0x03,                     //     Input (Constant, Variable, Absolute)

        0xa1.toByte(), 0x00,                     //   Collection (Physical)
        0x05, 0x09,                     //     Usage Page (Button)
        0x19, 0x01,                     //     Usage Minimum (Button 1)
        0x29, 0x10,                     //     Usage Maximum (Button 16)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x25, 0x01,                     //     Logical Maximum (1)
        0x75, 0x01,                     //     Report Size (1)
        0x95.toByte(), 0x10,                     //     Report Count (16)
        0x81.toByte(), 0x02,                     //     Input (Data, Variable, Absolute)

        0x75, 0x10,                   //     Report Size (16)
        0x16, 0x00, 0x80.toByte(),             //     Logical Minimum (-32768)
        0x26, 0xff.toByte(), 0x7f,             //     Logical Maximum (32767)
        0x36, 0x00, 0x80.toByte(),             //     Physical Minimum (-32768)
        0x46, 0xff.toByte(), 0x7f,             //     Physical Maximum (32767)
        0x05, 0x01,                   //     Usage Page (Generic Desktop)
        0x09, 0x01,                   //     Usage (Pointer)
        0xa1.toByte(), 0x00,                   //     Collection (Physical)
        0x95.toByte(), 0x02,                   //       Report Count (2)
        0x05, 0x01,                   //       Usage Page (Generic Desktop)
        0x09, 0x30,                   //       Usage (X)
        0x09, 0x31,                   //       Usage (Y)
        0x81.toByte(), 0x02,                   //       Input (Data, Variable, Absolute)
        0xc0.toByte(),                         //     End Collection
        0xc0.toByte(),                         //   End Collection
        0xc0.toByte(),                         // End Collection
    )

    fun checkPermission() {
        permissionList.forEach {
            val cameraPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                        it
                )
            if(cameraPermission != PackageManager.PERMISSION_GRANTED) {
                // 권한이 없는 경우 permission 권한을 띄우는 알람창을 띄운다.
                ActivityCompat.requestPermissions(this, permissionList.toTypedArray(), 1000)
            } else {
                // 권한이 있는 경우
                Toast.makeText(this, "카메라를 실행합니다", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun btConnect(device: BluetoothDevice?) {

//        Log.i(TAG, "btConnect: device=$device")

        // disconnect from everything else
        for (btDev in mBtHidDevice!!.getDevicesMatchingConnectionStates(
            intArrayOf( //BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_CONNECTED
            )
        )) {
            mBtHidDevice!!.disconnect(btDev)
            Log.i(TAG, "btConnect: disconnect")
        }
        /*  if(mBtHidDevice.getDevicesMatchingConnectionStates(new int[]{
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTING
        }).isEmpty()
                && device != null) {
            mBtHidDevice.connect(device);
            Log.i(TAG, "btConnect: connect" );
        }*/
        if (device != null) {
            mBtDevice = device
            mBtHidDevice!!.connect(device)

            Log.i(TAG, "btConnect: ${mBtHidDevice!!.connectedDevices} |<-- $device")

        }
    }
    private fun getProxy() {
        mBtAdapter.adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            @SuppressLint("NewApi", "MissingPermission")
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    Log.d(TAG, "Got HID device")
                    mBtHidDevice = proxy as BluetoothHidDevice
                    Log.d(TAG, "Get HID device")
                    mBluetoothHidDeviceAppQosSettings =
                        BluetoothHidDeviceAppQosSettings(1, 800, 9, 0, 11250, -1)

                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "BleHid",
                        "Android BLE HID Service",
                        "Android",
                        0x00,
                        descriptor
                    )
                    mBtHidDevice!!.registerApp(
                        sdp,
                        null,
                        mBluetoothHidDeviceAppQosSettings,
                        Executors.newSingleThreadExecutor(),
                        object : BluetoothHidDevice.Callback() {
                            override fun onGetReport(
                                device: BluetoothDevice,
                                type: Byte,
                                id: Byte,
                                bufferSize: Int,
                            ) {
                                Log.v(
                                    TAG, "onGetReport: device=" + device + " type=" + type
                                            + " id=" + id + " bufferSize=" + bufferSize
                                )
                            }

                            override fun onConnectionStateChanged(
                                device: BluetoothDevice,
                                state: Int,
                            ) {
                                if (device == mBtDevice) {
                                    when (state) {
                                        BluetoothProfile.STATE_DISCONNECTED -> {
                                            states.value = "STATE_DISCONNECTED"
                                            mBtDevice = null
                                        }
                                        BluetoothProfile.STATE_CONNECTING -> {
                                            states.value = "STATE_CONNECTING"
                                        }
                                        BluetoothProfile.STATE_CONNECTED -> {
                                            states.value = "STATE_CONNECTED"
                                        }
                                        BluetoothProfile.STATE_DISCONNECTING -> {
                                            states.value = "STATE_DISCONNECTING"
                                        }
                                    }
//                                    btConnect(device);
                                }
                                Log.v(
                                    TAG,
                                    "onConnectionStateChanged: device=$device deviceName=${device.name} state=${states.value}"
                                )
                            }
                        })
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    Log.d(TAG, "Lost HID device")
                }
            }

        }, BluetoothProfile.HID_DEVICE)
    }

    private fun btListDevices() {
        if ((ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            || (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ),
                1
            )
            return
        }
        getProxy()
        names.value.clear()
        mDevices.value.clear()
        val pairedDevices = mBtAdapter.adapter.bondedDevices
        Log.d("mDevice", "(disconnected)")
        Log.d("mDevice", "${pairedDevices.size}")


        pairedDevices.forEach{
            Log.d("mDevice", it.name)
            names.value.add(it.name)
            mDevices.value.add(it)
        }
    }
    @SuppressLint("RestrictedApi", "WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()
        mBtAdapter = this.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (!mBtAdapter.adapter.isEnabled) {
            mBtAdapter.adapter.enable()
        } else {
            btListDevices()
            Log.e("BTBT", "")
        }
        setContent {
            val haptic = LocalHapticFeedback.current
            localView = LocalView.current
            CameraTestTheme {
                // A surface container using the 'background' color from the theme
                var declarationDialogState by remember {
                    mutableStateOf(true)
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box (
                        modifier = Modifier
                            .fillMaxSize()
//                            .clickable { Capture() }
                        ,
                        contentAlignment = Alignment.Center
                    ){

                        if (declarationDialogState) {
                            DeclarationDialog() // 다이얼로그 컴포즈
                            { declarationDialogState = false } // 다이얼로그를 숨기는 unit 함수를 인자로 줌
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CameraView(
                                Modifier
                                    .size(300.dp, 400.dp)
//                                    .fillMaxWidth()
//                                    .height(300.dp)
                                    .border(3.dp, Color.Cyan))
//                            Button(onClick = {
////                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
//                                localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
//                                if(CameraSelector.DEFAULT_BACK_CAMERA == cameraSelector) {
//                                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//                                } else {
//                                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//                                }
//
//                                startCam(this@MainActivity, previewView, lifecycleOwner)
//
//                                Toast
//                                    .makeText(baseContext, "!!LensChange!!", Toast.LENGTH_SHORT)
//                                    .show()
//                                             }, colors = ButtonDefaults.buttonColors(
//                                containerColor = Transparent,
//                                disabledContainerColor = Transparent,
//                                contentColor = Color.Cyan,
//                                disabledContentColor = Transparent),
//                                interactionSource = remember { NoRippleInteractionSource() },
//                                modifier = Modifier.bounceClick()
//                            ) {
//                                Icon(Icons.Rounded.FavoriteBorder, contentDescription= "", modifier = Modifier.size(50.dp))
//                            }

                            Text(text = "${x.value} ${y.value}")
                            Row(modifier = Modifier.padding(top = 10.dp)) {
                                Icon(Icons.Rounded.FavoriteBorder, contentDescription= "", modifier = Modifier
                                    .size(50.dp)
                                    .bounceClick()
                                    .MixClick(
                                        interactionSource = remember { NoRippleInteractionSource() },
                                        onClick = {
//                                        toast?.cancel()
                                            Capture()
//                                        toast = Toast
//                                            .makeText(
//                                                baseContext,
//                                                "!!Capture!!",
//                                                Toast.LENGTH_SHORT
//                                            )
//                                        toast!!.show()

                                        },
                                        onLongClick = {
                                            localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            if (cameraIndex.value == 1) {
//                                            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                                cameraIndex.value = 3
                                                makerBias.value = false
                                            } else {
                                                cameraIndex.value = 1
                                                makerBias.value = true

//                                            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                            }


                                            startCam(this@MainActivity, previewView, lifecycleOwner)
                                            toast?.cancel()
                                            toast = Toast
                                                .makeText(
                                                    baseContext,
                                                    "!!LensChange!!",
                                                    Toast.LENGTH_SHORT
                                                )
                                            toast!!.show()
                                        },
                                        onDoubleClick = {
                                            Log.d(
                                                "CameraInfos",
                                                androidx.camera.core.CameraInfo.IMPLEMENTATION_TYPE_CAMERA2_LEGACY
                                            )
                                            cameraSelector = CameraSelector
                                                .Builder()
                                                .requireLensFacing(3)
                                                .build()
                                            startCam(this@MainActivity, previewView, lifecycleOwner)

                                        }


                                    )
                                )
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription= "", modifier = Modifier
                                    .size(50.dp)
                                    .bounceClick()
                                    .MixClick(
                                        interactionSource = remember { NoRippleInteractionSource() },
                                        onClick = {
//                                            Nomal()
                                            declarationDialogState = true
                                        }
                                    )
                                )
                                Icon(Icons.Rounded.Build, contentDescription= "", modifier = Modifier
                                    .size(50.dp)
                                    .bounceClick()
                                    .MixClick(
                                        interactionSource = remember { NoRippleInteractionSource() },
                                        onClick = {
                                            var modi: Byte = 0
                                            var state: Byte = 0
//                                            modi = (1 shl 0)
                                            Log.e(
                                                TAG,
                                                "SendRepo : ${mBtHidDevice?.connectedDevices}"
                                            )
                                            mBtHidDevice?.connectedDevices?.forEach { btdev ->
                                                /**
                                                 * 마우스 HID 형식
                                                 * byteArrayOf(0x00, 36.toByte(), 0.toByte(), 0.toByte())
                                                 *
                                                 * 1번째 부터
                                                 * 클릭, 가로이동, 세로이동, 휠
                                                 */

                                                val a = mBtHidDevice!!.sendReport(
                                                    btdev,
                                                    1
                                                        .toByte()
                                                        .toInt(),
                                                    byteArrayOf(
                                                        0x00,
                                                        36.toByte(),
                                                        0.toByte(),
                                                        0.toByte()
                                                    )
                                                )
                                                mBtHidDevice!!.sendReport(
                                                    btdev,
                                                    1
                                                        .toByte()
                                                        .toInt(),
                                                    byteArrayOf(
                                                        0x00,
                                                        0.toByte(),
                                                        0.toByte(),
                                                        0.toByte()
                                                    )
                                                )


                                                /**
                                                 * 키보드 HID 형식
                                                 * TODO 언젠가는 하겠지
                                                 */
                                                mBtHidDevice!!.sendReport(
                                                    btdev,
                                                    0x02
                                                        .toByte()
                                                        .toInt(),
                                                    byteArrayOf(0x00, 36.toByte())
                                                )
                                                mBtHidDevice!!.sendReport(
                                                    btdev,
                                                    0x02
                                                        .toByte()
                                                        .toInt(),
                                                    byteArrayOf(0x00, 0.toByte())
                                                )

                                                Log.e(
                                                    TAG,
                                                    "Sends : $a"
                                                )

                                            }

                                        }
                                    )
                                )

                            }
                        }
                    }
//                    Greeting("Android")
                }
            }
        }
    }
    fun send() {
        val bounds: Range<Int> = Range(-127, 127)

    }
    fun Nomal() {
        /**
         * 0:WIRIST
         * 8:INDEX_FINGER_TIP
         */
        res.value.results[0].landmarks().forEachIndexed{ index, it ->
            Log.d("landMaker | ${index}", "========================================|<-")
            it.get(0)
            it.get(8)

        }
//                Log.d("DraW X", drawContext.size.width.toString())
//                Log.d("DraW Y", drawContext.size.height.toString())

    }
    fun Capture() {

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Hello_Android_${SystemClock.uptimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/CameraX_test_App "
                )
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()
        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    toast?.cancel()
                    toast = Toast
                        .makeText(
                            baseContext,
                            msg,
                            Toast.LENGTH_SHORT
                        )
                    toast!!.show()
//                    Toast
////                        .makeText(baseContext, msg, Toast.LENGTH_SHORT)
//                        .makeText(baseContext, "!!CAPTURE!!", Toast.LENGTH_SHORT)
//                        .show()
//                    Log.d(TAG, msg)
                }
            }
        )
    }
    @Composable
    fun CameraView(modifier:Modifier) {
        lifecycleOwner = LocalLifecycleOwner.current
        Surface(modifier) {
            backgroundExecutor = Executors.newSingleThreadExecutor()
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    previewView = PreviewView(context).apply {
                        this.scaleType = scaleType
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.FILL_PARENT,
                            ViewGroup.LayoutParams.FILL_PARENT
                        )
                    }
                    startCam(context, previewView, lifecycleOwner)
                    previewView
                }
            )
            backgroundExecutor.execute {
                handLandmarkerHelper = HandLandmarkerHelper(
                    context = this,
                    runningMode = RunningMode.LIVE_STREAM,
                    minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                    minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                    minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                    maxNumHands = viewModel.currentMaxHands,
                    currentDelegate = HandLandmarkerHelper.DELEGATE_CPU,
                    handLandmarkerHelperListener = this
                )
            }
            landmarker(modifier = Modifier.fillMaxSize())

        }
    }
    @Composable
    fun landmarker(modifier: Modifier) {
        val cn = LocalContext.current
        Canvas(modifier = modifier.apply {
//            background(Color.Black)
        }, onDraw = {
//            drawCircle(Cyan, radius = 50f, center = Offset(1100F, 1100F))
            if(res.value.inferenceTime > 0L) {
                res.value.results[0].landmarks().forEachIndexed{ index, it ->
                    Log.d("landMaker | ${index}", "========================================|<-")

                    it.forEach{ap ->
                        drawCircle(Cyan, 10f, Offset(
//                        drawCircle(Cyan, 10f+drawContext.size.width * abs(ap.z())*0.3f, Offset(
                            x= drawContext.size.width * ap.x(),
                            y= drawContext.size.height * ap.y()
                        ))
                    }
                    val wristZ = it[0].z() * drawContext.size.width;

                    val z = wristZ + it[8].z()

                    Log.d("landMaker | ${index}", "${(z+0.6)} || ${it.get(0).z()}")
                    makers.x = (drawContext.size.height * it.get(0).x()).toDouble()
                    makers.y = (drawContext.size.height * it.get(0).y()).toDouble()
                    makers.z = (drawContext.size.height * it.get(0).z()).toDouble()

                    subMakers.x = (drawContext.size.height * it.get(0).x()).toDouble()
                    subMakers.y = (drawContext.size.height * it.get(0).y()).toDouble()
                    subMakers.z = (drawContext.size.height * it.get(0).z()).toDouble()

                    drawCircle(Red, 20f * (1f * (wristZ + it[0].z())), Offset(
//                        drawCircle(Cyan, 10f+drawContext.size.width * abs(ap.z())*0.3f, Offset(
                        x= drawContext.size.width * it.get(0).x(),
                        y= drawContext.size.height * it.get(0).y()
                    ))
                    drawCircle(Red, 20f*(1f - (z+0.6f)), Offset(
//                        drawCircle(Cyan, 10f+drawContext.size.width * abs(ap.z())*0.3f, Offset(
                        x= drawContext.size.width * it.get(8).x(),
                        y= drawContext.size.height * it.get(8).y()
                    ))

                    drawCircle(Red, 20f, Offset(
//                        drawCircle(Cyan, 10f+drawContext.size.width * abs(ap.z())*0.3f, Offset(
                        x= drawContext.size.width * it.get(5).x(),
                        y= drawContext.size.height * it.get(5).y()
                    ))

                    drawCircle(
                        Blue, 20f, Offset(
//                        drawCircle(Cyan, 10f+drawContext.size.width * abs(ap.z())*0.3f, Offset(
                        x= drawContext.size.width * it.get(12).x(),
                        y= drawContext.size.height * it.get(12).y()
                    ))
                    drawCircle(Blue, 20f, Offset(
//                        drawCircle(Cyan, 10f+drawContext.size.width * abs(ap.z())*0.3f, Offset(
                        x= drawContext.size.width * it.get(4).x(),
                        y= drawContext.size.height * it.get(4).y()
                    ))
                }
//                Log.d("DraW X", drawContext.size.width.toString())
//                Log.d("DraW Y", drawContext.size.height.toString())
            }
        })
    }
    @SuppressLint("UnsafeOptInUsageError")
    fun startCam(context: Context, previewView: PreviewView, lifecycleOwner: LifecycleOwner) {



        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

//            val cam2Infos = cameraProvider.availableCameraInfos.map {
//                Camera2CameraInfo.from(it)
//            }.sortedByDescending {
//                // HARDWARE_LEVEL is Int type, with the order of:
//                // LEGACY < LIMITED < FULL < LEVEL_3 < EXTERNAL
//                it.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
//            }
//            cam2Infos.forEach{
//                Log.e("Cam2Info", it.cameraId)
//            }
            // Preview
            val preview = androidx.camera.core.Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()
            val imageAnalyzer =
                ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    // The analyzer can then be assigned to the instance
                    .also {
                        it.setAnalyzer(backgroundExecutor) { image ->
                            detectHand(image)
//                            Log.e("Camera", image.height.toString())
                        }
                    }
            fun selectExternalOrBestCamera(provider: ProcessCameraProvider):CameraSelector? {
                val cam2Infos = provider.availableCameraInfos.map {
                    Camera2CameraInfo.from(it)
                }.sortedByDescending {
                    // HARDWARE_LEVEL is Int type, with the order of:
                    // LEGACY < LIMITED < FULL < LEVEL_3 < EXTERNAL
                    it.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                }

                return when {
                    cam2Infos.isNotEmpty() -> {
                        CameraSelector.Builder()
                            .addCameraFilter {
                                it.filter { camInfo ->
                                    // cam2Infos[0] is either EXTERNAL or best built-in camera
                                    val thisCamId = Camera2CameraInfo.from(camInfo).cameraId
                                    thisCamId == cam2Infos[this.cameraIndex.value].cameraId
//                                    thisCamId == cam2Infos[3].cameraId
                                }
                            }.build()
                    }
                    else -> null
                }
            }


//            val asas = HandLandmarkerHelper(context = ))
//            asas.detectLiveStream()
            // Select back camera as a default
//                    Log.e("Kamera", "${android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT}")
            try {
                val selector = selectExternalOrBestCamera(cameraProvider)

                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, selector!!, preview, imageCapture, imageAnalyzer
                )


            } catch(exc: Exception) {
                Log.e("Kamel", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }
    private fun detectHand(imageProxy: ImageProxy) {
        handLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = makerBias.value
        )
//        Log.e("Camera", "")

    }
    @SuppressLint("MissingPermission")
    override fun onResults(
        resultBundle: HandLandmarkerHelper.ResultBundle,
    ) {
//        result = resultBundle
        this.res.value = resultBundle
        if(resultBundle.inferenceTime > 0L) {

            resultBundle.results[0].landmarks().forEach {
                val pink = it[8]
                val black = it[0]

                if(toggle) {
                    accX = pink.x()
//                accX *= -1
                    accY = pink.y()
//                accY *= -1

                    toggle != toggle
                } else {
                    accX -= pink.x()
//                accX *= -1
                    accY -= pink.y()
                    mBtHidDevice?.connectedDevices?.forEach { btdev ->
                        /**
                         * 마우스 HID 형식
                         * byteArrayOf(0x00, 36.toByte(), 0.toByte(), 0.toByte())
                         *
                         * 1번째 부터
                         * 클릭, 가로이동, 세로이동, 휠
                         */
//                        GlobalScope.launch {
//                            mBtHidDevice!!.sendReport(
//                                btdev, 1.toByte().toInt(), byteArrayOf(0x00, (accX*400f *-1).toInt().toByte(), (accY*400f *-1).toInt().toByte(), 0.toByte())
//                            )
//                            mBtHidDevice!!.sendReport(
//                                btdev, 1.toByte().toInt(), byteArrayOf(0x00, 0.toByte(), 0.toByte(), 0.toByte())
//                            )
//                        }
                        mBtHidDevice!!.sendReport(
                            btdev, 1.toByte().toInt(), byteArrayOf(0x00, (accX*400f *-1).toInt().toByte(), (accY*400f *-1).toInt().toByte(), 0.toByte())
                        )
                        mBtHidDevice!!.sendReport(
                            btdev, 1.toByte().toInt(), byteArrayOf(0x00, 0.toByte(), 0.toByte(), 0.toByte())
                        )


                    }

                    Log.e("ASI", "${1000 * accX} || ${1000 * accY}")
                    accX = pink.x()
                    accY = pink.y()

                }
            }


        } else {
            toggle = true
            accX =0f
            accY =0f
        }


//        if(this.res.value.results.get(0).landmarks().size > 0) {
//            this.x.value = resultBundle.results.get(0).landmarks().get(0).get(0).z()
//            this.y.value = resultBundle.results.get(0).landmarks().get(0).get(0).z()
//        }

//        Log.e("HelloWorld!", this.res.toString())
//        resultBundle.results.get(0).landmarks(

    }

    override fun onError(error: String, errorCode: Int) {}

    @SuppressLint("MissingPermission")
    @Composable
    fun DeclarationDialog(onChangeState: () -> Unit) {

        val declarations = listOf("부적절한 언어 사용(욕설, 비속어)", "불쾌함 유발", "어쨋든 잘못함", "기타")
        btListDevices()
        AlertDialog(

            // 다이얼로그 뷰 밖의 화면 클릭시, 인자로 받은 함수 실행하며 다이얼로그 상태 변경
            onDismissRequest = { onChangeState() },
            title = { Text(text = "Connected", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            text = {
                Column {
//                    Text(text = "연결하기", modifier = Modifier.padding(bottom = 5.dp))
                    mDevices.value.forEach {
                        Row(modifier = Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "${it?.name}", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                            Icon(Icons.Rounded.Send, contentDescription= "", modifier = Modifier
                                .size(20.dp)
                                .bounceClick()
                                .MixClick(
                                    interactionSource = remember { NoRippleInteractionSource() },
                                    onClick = {
                                        btConnect(it)
                                        onChangeState()
                                        Log.d(TAG, mBtHidDevice?.connectedDevices.toString())


                                    }
                                )
                            )
                        }
                    }
                }
            },
//        dismissButton = {
//            // 취소 버튼 클릭시 인자로 받은 함수 실행하며 다이얼로그 상태 변경
//            TextButton(onClick = { onChangeState() }) {
//                Text(text = "취소", color = Color.Black)
//            }
//        },
            confirmButton = {
                // 신고 버튼 클릭시 인자로 받은 함수 실행하며 다이얼로그 상태 변경
//                TextButton(
//                    onClick = {
//                        // 원하는 실행문 작성
//                        onChangeState()
//                    }) {
//                    Text(text = "신고", color = Color.Black)
//                }
            }
        )

    }
}

class NoRippleInteractionSource : MutableInteractionSource {

    override val interactions: Flow<Interaction> = emptyFlow()

    override suspend fun emit(interaction: Interaction) {}

    override fun tryEmit(interaction: Interaction) = true
}

enum class ButtonState { Pressed, Idle }
fun Modifier.bounceClick() = composed {
    /**
     * https://blog.canopas.com/jetpack-compose-cool-button-click-effects-c6bbecec7bcb
     * 위 글을 참고 하였다.
    **/
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    val scale by animateFloatAsState(if (buttonState == ButtonState.Pressed) 0.70f else 1f,
        label = ""
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { }
        )
        .pointerInput(buttonState) {
            awaitPointerEventScope {
                buttonState = if (buttonState == ButtonState.Pressed) {
                    waitForUpOrCancellation()
                    ButtonState.Idle
                } else {
                    awaitFirstDown(false)
                    ButtonState.Pressed
                }
            }
        }
}

//fun Modifier.MixClick(
//    enabled: Boolean = true,
//    onClickLabel: String? = null,
//    role: Role? = null,
//    interactionSource: MutableInteractionSource,
//    onClick: () -> Unit
//) = composed(
//    inspectorInfo = debugInspectorInfo {
//        name = "clickable"
//        properties["enabled"] = enabled
//        properties["onClickLabel"] = onClickLabel
//        properties["role"] = role
//        properties["onClick"] = onClick
//    }
//) {
//    Modifier.clickable(
//        enabled = enabled,
//        onClickLabel = onClickLabel,
//        onClick = onClick,
//        role = role,
//        indication = LocalIndication.current,
//        interactionSource = interactionSource
//    )
//}
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.MixClick(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "combinedClickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
        properties["onDoubleClick"] = onDoubleClick
        properties["onLongClick"] = onLongClick
        properties["onLongClickLabel"] = onLongClickLabel
    }
) {
    Modifier.combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick,
        role = role,
        indication = LocalIndication.current,
        interactionSource = interactionSource
    )
}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CameraTestTheme {
        Greeting("Android")
    }
}