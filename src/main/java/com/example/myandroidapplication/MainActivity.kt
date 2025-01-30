package com.example.myandroidapplication

import RetrofitClient
import TranscriptionResponse
import android.Manifest
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.myandroidapplication.ui.theme.MyAndroidApplicationTheme
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File


private lateinit var mediaRecorder: MediaRecorder
private lateinit var audioFile: File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        setContent {
            MyAndroidApplicationTheme {
                var isRecording by remember { mutableStateOf(false) }
                var transcriptions by remember { mutableStateOf(listOf<String>()) }

                RecordingUI(
                    isRecording = isRecording,
                    onStartRecording = {
                        startRecording()
                        isRecording = true
                    },
                    onStopRecording = {
                        stopRecording { transcription ->
                            transcriptions = transcriptions + transcription
                        }
                        isRecording = false
                    },
                    onClearTranscriptions = {
                        transcriptions = listOf() // Clear the transcription list
                    },
                    transcriptions = transcriptions
                )
            }
        }
    }

    private fun startRecording() {
        val musicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (musicDir != null && !musicDir.exists()) {
            val dirCreated = musicDir.mkdirs()
            Log.d("FilePath", "Music directory created: $dirCreated")
        }

        val fileName = "audio_${System.currentTimeMillis()}.wav"
        audioFile = File(musicDir, fileName)

        Log.d("FilePath", "Audio file path: ${audioFile.absolutePath}")

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MediaRecorder", "Error starting recording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun stopRecording(updateTranscriptions: (String) -> Unit) {
        try {
            mediaRecorder.stop()
            mediaRecorder.release()
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()

            if (audioFile.exists()) {
                Log.d("FilePath", "Recording saved: ${audioFile.absolutePath}")
                sendAudioToFlask(audioFile, updateTranscriptions)
            } else {
                Log.e("FilePath", "File not found after recording")
            }
        } catch (e: Exception) {
            Log.e("MediaRecorder", "Error stopping recording: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error stopping recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendAudioToFlask(audioFile: File, updateTranscriptions: (String) -> Unit) {
        val requestBody = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Part.createFormData("file", audioFile.name, requestBody)

        RetrofitClient.instance.transcribeAudio(multipartBody)
            .enqueue(object : Callback<TranscriptionResponse> {
                override fun onResponse(
                    call: Call<TranscriptionResponse>,
                    response: Response<TranscriptionResponse>
                ) {
                    if (response.isSuccessful) {
                        val transcription = response.body()?.transcription.orEmpty()
                        Log.d("Transcription", "Received: $transcription")
                        updateTranscriptions(transcription)
                    } else {
                        Log.e("Transcription", "Error: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<TranscriptionResponse>, t: Throwable) {
                    Log.e("Transcription", "Failure: ${t.message}")
                }
            })
    }

    private fun checkPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1
        )
    }
}

@Composable
fun RecordingUI(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClearTranscriptions: () -> Unit,
    transcriptions: List<String>
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Invoke TranscriptionList to display transcriptions
        TranscriptionList(
            transcriptions = transcriptions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopStart) // Position at the top
        )

        // Recording and Clear buttons at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .align(Alignment.BottomCenter),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recording Button
                RecordingButton(
                    isRecording = isRecording,
                    onButtonClick = {
                        if (isRecording) {
                            onStopRecording()
                        } else {
                            onStartRecording()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(16.dp)) // Space between buttons

                // Clear Button
                ClearButton(
                    onClearClick = onClearTranscriptions
                )
            }
        }
    }
}


@Composable
fun ClearButton(onClearClick: () -> Unit) {
    IconButton(
        onClick = onClearClick,
        modifier = Modifier
            .size(80.dp)
            .background(Color.Gray, shape = CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Clear Transcriptions",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}




@Composable
fun TranscriptionList(
    transcriptions: List<String>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(transcriptions) { transcription ->
            Log.d("TranscriptionList", "Displaying: $transcription") // Debug log

            Text(
                text = transcription,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White) // Ensure contrast with the background
                    .padding(16.dp),
                color = Color.Black // Text color
            )
        }
    }
}






@Composable
fun RecordingButton(
    isRecording: Boolean,
    onButtonClick: () -> Unit
) {
    IconButton(
        onClick = onButtonClick,
        modifier = Modifier
            .size(80.dp)
            .background(
                if (isRecording) Color.Red else Color.Blue,
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}



