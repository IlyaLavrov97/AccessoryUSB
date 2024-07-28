package com.example.testusb

import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : ComponentActivity() {
    private val TAG = "USBHostExample"

    private lateinit var usbManager: UsbManager
    private var accessory: UsbAccessory? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbManager = getSystemService(USB_SERVICE) as UsbManager

        if (usbManager.accessoryList != null) {
            Log.d("AccessoryMode", "Устройство в состоянии аксессуара")
            Toast.makeText(
                this,
                "ПОЗДРАВЛЯЮ, ПОЛУЧИЛОСЬ ПОДКЛЮЧИТЬ КАК АКСЕССУАР",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Log.d("AccessoryMode", "Устройство НЕ в состоянии аксессуара")
            Toast.makeText(
                this,
                "Устройство НЕ в состоянии аксессуара",
                Toast.LENGTH_SHORT
            ).show()
        }

        enableEdgeToEdge()
        setContent {
            USBHostExampleApp(
                onSendClick = {
                    sendFile()
                }
            )
        }
    }

    private fun sendFile() {
        if (accessory == null) {
            Toast.makeText(this, "Не подключен аксессуар", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            outputStream?.write("Hello, PC!".toByteArray())
            Toast.makeText(this, "Данные отправленны", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "Error sending data", e)
        }
    }

    override fun onResume() {
        super.onResume()

        intent?.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)?.let {
            openAccessory(it)
        }
    }

    private fun openAccessory(accessory: UsbAccessory) {
        fileDescriptor = usbManager.openAccessory(accessory)
        fileDescriptor?.fileDescriptor?.let { fd ->
            this.accessory = accessory
            inputStream = FileInputStream(fd)
            outputStream = FileOutputStream(fd)
            Log.d(TAG, "Accessory opened")
        } ?: run {
            Log.e(TAG, "Accessory open failed")
        }
    }

    override fun onPause() {
        super.onPause()
        closeAccessory()
    }

    private fun closeAccessory() {
        try {
            fileDescriptor?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing accessory", e)
        } finally {
            fileDescriptor = null
            accessory = null
        }
    }
}

@Composable
fun USBHostExampleApp(onSendClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Button(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(90.dp),
                onClick = onSendClick
            ) {
                Text(
                    text = "Send Data",
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
