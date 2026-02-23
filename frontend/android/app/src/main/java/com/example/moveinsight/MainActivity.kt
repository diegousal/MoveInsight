package com.example.moveinsight

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.example.moveinsight.ui.theme.MoveInsightTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- COLORES PERSONALIZADOS ---
val PrimaryColor = Color(0xFF00695C) // Teal Intenso
val SecondaryColor = Color(0xFFB2DFDB) // Teal Suave
val BackgroundColor = Color(0xFFF5F7F8) // Gris muy claro
val TextColor = Color(0xFF37474F) // Gris Pizarra

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoveInsightTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BackgroundColor
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.padding(innerPadding),
                        color = BackgroundColor
                    ) {
                        MoveInsightScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun MoveInsightScreen() {
    val context = LocalContext.current

    // ESTADOS
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempVideoUri by remember { mutableStateOf<Uri?>(null) }

    // --- CONFIGURACIÓN COIL (VIDEO) ---
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    // --- LAUNCHERS ---
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) mediaUri = uri
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) mediaUri = tempPhotoUri
    }

    // Corregido: CaptureVideo devuelve Boolean (success)
    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) mediaUri = tempVideoUri
    }

    // --- UI ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // 1. TÍTULO
        Text(
            text = "MoveInsight",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = PrimaryColor
        )
        Text(
            text = "Análisis de movimiento",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 2. VISUALIZADOR
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (mediaUri != null) {
                    AsyncImage(
                        model = mediaUri,
                        imageLoader = imageLoader,
                        contentDescription = "Media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Usamos Icons.Default.Image que es más estándar y seguro
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Selecciona o captura", color = Color.LightGray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. FEEDBACK DE ÉXITO
        AnimatedVisibility(
            visible = mediaUri != null,
            enter = fadeIn() + slideInVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(SecondaryColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = PrimaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Archivo cargado correctamente",
                    color = PrimaryColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 4. BOTONERA
        if (mediaUri == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón Galería
                OutlinedIconButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    icon = Icons.Default.Image, // Icono seguro
                    text = "Galería",
                    modifier = Modifier.weight(1f)
                )

                // Botón Foto
                OutlinedIconButton(
                    onClick = {
                        val uri = createTempPictureUri(context)
                        tempPhotoUri = uri
                        cameraPhotoLauncher.launch(uri)
                    },
                    icon = Icons.Default.PhotoCamera,
                    text = "Foto",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Botón Video
            PrimaryButton(
                text = "Grabar Análisis",
                icon = Icons.Default.Videocam,
                onClick = {
                    val uri = createTempVideoUri(context)
                    tempVideoUri = uri
                    cameraVideoLauncher.launch(uri)
                }
            )
        } else {
            // Botones cuando hay archivo
            OutlinedButton(
                onClick = { mediaUri = null },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Text("Cambiar archivo", color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    Toast.makeText(context, "Enviando al servidor...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                // Usamos el icono AutoMirrored para evitar el aviso de deprecated
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analizar Movimiento", fontSize = 18.sp)
            }
        }
    }
}

// --- COMPONENTES REUTILIZABLES ---

@Composable
fun PrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun OutlinedIconButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, PrimaryColor)
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryColor)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = PrimaryColor)
    }
}

// --- FUNCIONES URI CORREGIDAS ---

fun createTempPictureUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    val storageDir = context.externalCacheDir
    val image = File.createTempFile(imageFileName, ".jpg", storageDir)

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        image
    )
}

fun createTempVideoUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val videoFileName = "MP4_" + timeStamp + "_"
    val storageDir = context.externalCacheDir
    val video = File.createTempFile(videoFileName, ".mp4", storageDir)

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        video
    )
}