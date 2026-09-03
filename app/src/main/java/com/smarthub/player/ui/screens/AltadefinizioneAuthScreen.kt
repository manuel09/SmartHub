package com.smarthub.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthub.player.data.proxy.AltadefinizioneAuthManager
import com.smarthub.player.data.proxy.TelegramLoginInfo
import com.smarthub.player.ui.TvUtils
import com.smarthub.player.ui.theme.VixRed
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*

private const val PAIRING_PORT = 8765
private const val PAIRING_TIMEOUT_MS = 120_000
private const val LOGIN_TIMEOUT_MS = 600_000L

@Composable
fun AltadefinizioneAuthScreen(
    onCookieExtracted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTelevision(context) }

    if (isTv) {
        TVPairingScreen(onCookieExtracted = onCookieExtracted, onDismiss = onDismiss)
    } else {
        MobileAuthScreen(onCookieExtracted = onCookieExtracted, onDismiss = onDismiss)
    }
}

// ─── TV: Pairing via codice ─────────────────────────────────────────────

@Composable
private fun TVPairingScreen(
    onCookieExtracted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pairingCode by remember { mutableStateOf("") }
    var localIp by remember { mutableStateOf("...") }
    var status by remember { mutableStateOf("pairing") } // pairing | success | error | timeout
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var serverJob by remember { mutableStateOf<Job?>(null) }

    fun startPairingServer() {
        val code = (100000..999999).random().toString()
        pairingCode = code
        localIp = getLocalIpAddress()

        serverJob?.cancel()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val serverSocket = ServerSocket(PAIRING_PORT, 1, InetAddress.getByName("0.0.0.0"))
                serverSocket.soTimeout = PAIRING_TIMEOUT_MS

                try {
                    val socket = serverSocket.accept()
                    try {
                        val input = BufferedReader(InputStreamReader(socket.inputStream))
                        var contentLength = 0
                        var methodLine = ""
                        var first = true
                        while (true) {
                            val line = input.readLine() ?: break
                            if (first) { methodLine = line; first = false }
                            if (line.isEmpty()) break
                            val lower = line.lowercase()
                            if (lower.startsWith("content-length:")) {
                                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                            }
                        }

                        val body = CharArray(contentLength)
                        if (contentLength > 0) {
                            var read = 0
                            while (read < contentLength) {
                                val c = input.read()
                                if (c == -1) break
                                body[read++] = c.toChar()
                            }
                        }

                        val bodyStr = String(body).trim()
                        // Accetta sia il JSON esatto sia qualsiasi payload che contiene il codice
                        val codeMatch = Regex("\"code\"\\s*:\\s*\"(\\d+)\"|(\\d{6,8})").find(bodyStr)
                        val cookieMatch = Regex("\"cookie\"\\s*:\\s*\"([^\"]+)\"").find(bodyStr)

                        val receivedCode = codeMatch?.let {
                            it.groupValues[1].ifBlank { it.groupValues[2] }
                        }

                        if (receivedCode != null && receivedCode == code && cookieMatch != null) {
                            val cookie = cookieMatch.groupValues[1]
                            withContext(Dispatchers.Main) {
                                status = "success"
                                onCookieExtracted(cookie)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                status = "error"
                                errorMsg = if (receivedCode == null) "Codice non corrispondente" else "Cookie non valido"
                            }
                        }

                        val response = if (status == "success") {
                            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nOK"
                        } else {
                            "HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nERR"
                        }
                        socket.outputStream.write(response.toByteArray())
                        socket.outputStream.flush()
                    } finally {
                        socket.close()
                    }
                } catch (_: SocketTimeoutException) {
                    withContext(Dispatchers.Main) {
                        status = "timeout"
                        errorMsg = "Timeout: nessun telefono connesso entro 2 minuti"
                    }
                } finally {
                    serverSocket.close()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status = "error"
                    errorMsg = "Errore server: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        startPairingServer()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (status) {
                "pairing" -> {
                    Icon(
                        Icons.Default.Close, null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp).padding(bottom = 24.dp)
                    )
                    Text(
                        text = "Abbina con il telefono",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "Collega il telefono alla stessa rete WiFi della TV.\n" +
                                "Poi apri l'app VixStream sul telefono\ne vai su Profilo → 'Invia cookie a TV'.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 28.dp)
                    )
                    Text(
                        text = "IP TV",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "$localIp:$PAIRING_PORT",
                        color = VixRed,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    Text(
                        text = "Codice",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = pairingCode,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 10.sp,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    CircularProgressIndicator(
                        color = VixRed,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "In attesa del telefono...",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                "success" -> {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(64.dp).padding(bottom = 20.dp)
                    )
                    Text(
                        text = "Cookie ricevuto!",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = VixRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }

                "timeout", "error" -> {
                    Text(
                        text = if (status == "timeout") "Timeout" else "Errore",
                        color = Color(0xFFEF5350),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = errorMsg,
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Chiudi")
                        }
                        Button(
                            onClick = {
                                status = "pairing"
                                startPairingServer()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VixRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Riprova")
                        }
                    }
                }
            }
        }
    }
}

// ─── Mobile: login nativo via bot Telegram ──────────────────────────────

@Composable
private fun MobileAuthScreen(
    onCookieExtracted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { AltadefinizioneAuthManager() }

    var stage by remember { mutableStateOf("idle") } // idle | starting | waiting | success | error
    var statusMsg by remember { mutableStateOf("") }
    var loginInfo by remember { mutableStateOf<TelegramLoginInfo?>(null) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    fun openTelegram() {
        val info = loginInfo ?: return
        val candidates = listOf(info.deeplink, info.weblink).filter { it.isNotBlank() }
        for (link in candidates) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) { }
        }
    }

    suspend fun pollForCompletion(nonce: String) {
        val start = System.currentTimeMillis()
        while (stage == "waiting" && System.currentTimeMillis() - start < LOGIN_TIMEOUT_MS) {
            delay(2000)
            val status = authManager.pollStatus(nonce)
            when (status) {
                "ready", "consumed" -> {
                    val cookie = authManager.completeSession(nonce)
                    if (cookie != null) {
                        stage = "success"
                        statusMsg = "Connesso!"
                        onCookieExtracted(cookie)
                    } else {
                        stage = "error"
                        statusMsg = "Sessione non creata. Riprova."
                    }
                    return
                }
                "channel_pending" -> {
                    statusMsg = "Login ok. Nel bot segui il canale e premi verifica."
                }
                "expired", "not_found" -> {
                    stage = "error"
                    statusMsg = "Link scaduto. Riprova."
                    return
                }
            }
        }
        if (stage == "waiting") {
            stage = "error"
            statusMsg = "Timeout. Riprova."
        }
    }

    fun startFlow() {
        stage = "starting"
        statusMsg = ""
        pollJob?.cancel()
        scope.launch {
            val info = authManager.startLogin()
            if (info == null) {
                stage = "error"
                statusMsg = "Impossibile contattare Altadefinizione. Riprova."
                return@launch
            }
            loginInfo = info
            stage = "waiting"
            statusMsg = "Apri Telegram e premi Start sul bot"
            openTelegram()
            pollJob = launch { pollForCompletion(info.nonce) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { pollJob?.cancel() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Login Altadefinizione",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (stage) {
                    "idle" -> {
                        Text(
                            text = "Sblocca il player con Telegram",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Premi il pulsante, poi apri Telegram e tocca Start sul bot.\n" +
                                    "Dovrai seguire il canale e premere verifica.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(bottom = 28.dp)
                        )
                        Button(
                            onClick = { startFlow() },
                            colors = ButtonDefaults.buttonColors(containerColor = VixRed),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                "Sblocca con Telegram",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    "starting" -> {
                        CircularProgressIndicator(color = VixRed, modifier = Modifier.size(40.dp))
                        Text(
                            text = "Preparazione...",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    "waiting" -> {
                        CircularProgressIndicator(color = VixRed, modifier = Modifier.size(40.dp))
                        Text(
                            text = statusMsg,
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            text = "Se Telegram non si è aperto, tocca qui sotto.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                        )
                        OutlinedButton(
                            onClick = { openTelegram() },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Riapri Telegram", fontSize = 14.sp)
                        }
                    }

                    "success" -> {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(64.dp).padding(bottom = 20.dp)
                        )
                        Text(
                            text = "Connesso!",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = VixRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("OK", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }

                    "error" -> {
                        Text(
                            text = "Errore",
                            color = Color(0xFFEF5350),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = statusMsg,
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Chiudi")
                            }
                            Button(
                                onClick = { startFlow() },
                                colors = ButtonDefaults.buttonColors(containerColor = VixRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Riprova")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Utils ─────────────────────────────────────────────────────────────

private fun getLocalIpAddress(): String {
    try {
        val candidates = mutableListOf<String>()
        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
            if (iface.isLoopback || !iface.isUp) return@forEach
            iface.inetAddresses.toList().forEach { addr ->
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: return@forEach
                    if (host.startsWith("192.") || host.startsWith("10.") || host.startsWith("172.")) {
                        val name = iface.name.lowercase()
                        val weight = when {
                            name.startsWith("eth") -> 3
                            name.startsWith("wlan") -> 2
                            name.startsWith("wifi") -> 2
                            name.contains("vpn") || name.contains("tun") -> 0
                            else -> 1
                        }
                        candidates.add("$weight#$host")
                    }
                }
            }
        }
        candidates.sortByDescending { it.substringBefore("#") }
        return candidates.firstOrNull()?.substringAfter("#") ?: "127.0.0.1"
    } catch (_: Exception) { }
    return "127.0.0.1"
}
