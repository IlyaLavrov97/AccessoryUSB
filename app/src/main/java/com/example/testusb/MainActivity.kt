package com.example.testusb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream


class MainActivity : ComponentActivity() {
    private val TAG = "USBHostExample"
    private val ACTION_USB_PERMISSION: String = "com.android.example.USB_PERMISSION"

    private lateinit var usbManager: UsbManager
    private var accessory: UsbAccessory? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbManager = getSystemService(USB_SERVICE) as UsbManager

        if (usbManager.accessoryList.isNullOrEmpty()) {
            Log.d("AccessoryMode", "Устройство НЕ в состоянии аксессуара")
            Toast.makeText(
                this,
                "Устройство НЕ в состоянии аксессуара",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            this.accessory = usbManager.accessoryList[0]
            Log.d("AccessoryMode", "Устройство в состоянии аксессуара")
            Toast.makeText(
                this,
                "ПОЗДРАВЛЯЮ, ПОЛУЧИЛОСЬ ПОДКЛЮЧИТЬ КАК АКСЕССУАР",
                Toast.LENGTH_SHORT
            ).show()
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var fileSize by remember { mutableLongStateOf(0L) }
            USBHostExampleApp(
                fileSize = fileSize,
                onSendClick = {
                    sendFile()
                },
                onReceiveClick = {
                    receiveData()
                },
                onConnectClick = {
                    connectAccessory()
                },
                onMediaSelected = { uri ->
                    fileSize = 0
                    val fileStream = openInputStreamFromUri(context, uri)
                    Toast.makeText(
                        this,
                        "Файл начинает грузиться в аксессуар",
                        Toast.LENGTH_SHORT
                    ).show()
                    sendFileInChunks(fileStream) { chunk ->
                        fileSize += chunk.size

                        try {
                            outputStream?.write(chunk)
                        } catch (e: IOException) {
                            Toast.makeText(
                                this,
                                "Произошла ошибка загрузки файла, смотри логи",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.e(TAG, "Error sending data", e)
                        }
                    }
                }
            )
        }
    }

    private fun openInputStreamFromUri(
        context: Context,
        uri: Uri?
    ): InputStream? {
        return uri?.let {
            context.contentResolver.openInputStream(it)
        }
    }

    private fun sendFileInChunks(
        inputStream: InputStream?,
        chunkSize: Int = 512,
        onChunkReady: (ByteArray) -> Unit
    ) {
        inputStream?.use { stream ->
            val buffer = ByteArray(chunkSize)
            var bytesRead: Int

            while (stream.read(buffer).also { bytesRead = it } != -1) {
                val chunk = if (bytesRead < chunkSize) {
                    buffer.copyOf(bytesRead)
                } else {
                    buffer
                }
                onChunkReady(chunk)
            }
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

    private fun receiveData() {
        if (accessory == null) {
            Toast.makeText(this, "Не подключен аксессуар", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val inputAsString = inputStream?.bufferedReader().use { it?.readText() } ?: "Пусто"
            Log.i("inputStream", inputAsString)
            Toast.makeText(this, "Получено:" + inputAsString.take(10), Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "Error receiving data", e)
        }
    }

    private fun connectAccessory() {
        if (usbManager.accessoryList.isNullOrEmpty())
            Toast.makeText(
                this,
                "СПИСОК ПУСТ",
                Toast.LENGTH_SHORT
            ).show()
        else
            openAccessory(usbManager.getAccessoryList()[0])
    }

    override fun onResume() {
        super.onResume()

        if (accessory == null) {
            Toast.makeText(
                this,
                "Аксессуар НЕ подключен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!usbManager.hasPermission(accessory))
            usbManager.requestPermission(
                accessory,
                PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        else {
            Toast.makeText(
                this,
                "Доступ к аксессуару ЕСТЬ",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openAccessory(accessory: UsbAccessory) {
        try {
            fileDescriptor = usbManager.openAccessory(accessory)
            fileDescriptor?.fileDescriptor?.let { fd ->
                inputStream = FileInputStream(fd)
                outputStream = FileOutputStream(fd)
                Log.d(TAG, "Accessory opened")
            } ?: run {
                Log.e(TAG, "Accessory open failed")
            }
        } catch (ex: Exception) {
            Toast.makeText(
                this,
                ex.message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onPause() {
        super.onPause()
        //closeAccessory()
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
fun USBHostExampleApp(
    fileSize: Long,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onConnectClick: () -> Unit,
    onMediaSelected: (Uri?) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = GetMediaActivityResultContract(),
        onResult = { uri -> onMediaSelected(uri) }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                Button(
                    modifier = Modifier
                        .padding(top = 30.dp)
                        .height(54.dp)
                        .fillMaxWidth(),
                    onClick = onConnectClick
                ) {
                    Text(
                        text = "Connect Accessory",
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Button(
                    modifier = Modifier
                        .padding(top = 30.dp)
                        .height(54.dp)
                        .fillMaxWidth(),
                    onClick = onSendClick
                ) {
                    Text(
                        text = "Send Data",
                        textAlign = TextAlign.Center
                    )
                }
            }


            item {
                Button(
                    modifier = Modifier
                        .padding(top = 30.dp)
                        .height(54.dp)
                        .fillMaxWidth(),
                    onClick = onReceiveClick
                ) {
                    Text(
                        text = "Receive Data",
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Button(
                    modifier = Modifier
                        .padding(top = 30.dp)
                        .height(54.dp)
                        .fillMaxWidth(),
                    onClick = { launcher.launch("*/*") }
                ) {
                    Text(
                        text = "Select photo or video"
                    )
                }

                if (fileSize > 0)
                    Text(
                        modifier = Modifier
                            .padding(top = 30.dp)
                            .height(54.dp)
                            .fillMaxWidth(),
                        text = "Загруженно $fileSize байт"
                    )
            }
        }
    }
}
