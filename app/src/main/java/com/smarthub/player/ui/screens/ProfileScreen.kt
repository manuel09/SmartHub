package com.smarthub.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthub.player.data.local.AppSettings
import com.smarthub.player.data.local.UserProfile
import com.smarthub.player.ui.MovieViewModel
import com.smarthub.player.ui.theme.VixRed
import kotlinx.coroutines.*
import java.net.URL

private const val PAIRING_PORT = 8765

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: MovieViewModel) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val autoPlayNextEpisode by viewModel.autoPlayNextEpisode.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf<UserProfile?>(null) }
    var deleteConfirm by remember { mutableStateOf<UserProfile?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAltadefAuth by remember { mutableStateOf(false) }
    var showSendToTv by remember { mutableStateOf(false) }
    var altadefCookie by remember { mutableStateOf(AppSettings.getAltadefinizioneCookie(context)) }

    val canDelete = profiles.size > 1

    if (showCreateDialog) {
        ProfileEditDialog(
            title = "Nuovo Profilo",
            onDismiss = { showCreateDialog = false },
            onSave = { name, email ->
                viewModel.createProfile(name, email)
                showCreateDialog = false
            }
        )
    }

    editProfile?.let { profile ->
        ProfileEditDialog(
            title = "Modifica Profilo",
            initialName = profile.name,
            initialEmail = profile.email,
            onDismiss = { editProfile = null },
            onSave = { name, email ->
                viewModel.updateProfile(profile.copy(name = name, email = email))
                editProfile = null
            }
        )
    }

    deleteConfirm?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            containerColor = Color(0xFF1F1F1F),
            title = { Text("Elimina profilo", color = Color.White) },
            text = { Text("Sei sicuro di voler eliminare il profilo '${profile.name}'?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile.id)
                    deleteConfirm = null
                }) {
                    Text("Elimina", color = VixRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) {
                    Text("Annulla", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = Color(0xFF1F1F1F),
            title = { Text("Cancella cronologia", color = Color.White) },
            text = { Text("Vuoi cancellare tutta la cronologia?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllContinueWatching()
                    showClearHistoryDialog = false
                }) {
                    Text("Cancella", color = VixRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Annulla", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showClearFavoritesDialog) {
        AlertDialog(
            onDismissRequest = { showClearFavoritesDialog = false },
            containerColor = Color(0xFF1F1F1F),
            title = { Text("Cancella preferiti", color = Color.White) },
            text = { Text("Vuoi cancellare tutti i preferiti?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllFavorites()
                    showClearFavoritesDialog = false
                }) {
                    Text("Cancella", color = VixRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFavoritesDialog = false }) {
                    Text("Annulla", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = Color(0xFF1F1F1F),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("VixStream", color = VixRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("App", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Versione: ${AppSettings.getAppVersion(context)}", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Un'app per guardare film e serie TV.", color = Color.Gray, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK", color = VixRed)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 32.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PROFILI",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isActive = profile.id == activeProfileId
                    Card(
                        onClick = { if (!isActive) viewModel.setActiveProfile(profile.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) VixRed.copy(alpha = 0.12f) else Color(0xFF1A1A1A)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = if (isActive) BorderStroke(2.dp, VixRed) else null
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(Color(profile.avatarColor), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(profile.initials, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    profile.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (isActive) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Attivo", color = VixRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            IconButton(
                                onClick = { editProfile = profile },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(30.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(Icons.Default.Edit, "Modifica", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                            }

                            if (canDelete) {
                                IconButton(
                                    onClick = { deleteConfirm = profile },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(30.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Delete, "Elimina", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                item(key = "add") {
                    Card(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Add, "Aggiungi profilo", tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Aggiungi", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            val activeProfile = profiles.find { it.id == activeProfileId }
            if (activeProfile != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(activeProfile.avatarColor), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(activeProfile.initials, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeProfile.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (activeProfile.email.isNotBlank()) {
                            Text(activeProfile.email, color = Color.Gray, fontSize = 12.sp)
                        }
                        Text("Profilo attivo", color = VixRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (profiles.isEmpty()) {
                Card(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PersonAdd, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Crea il tuo primo profilo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Ogni profilo ha la sua cronologia e i suoi preferiti", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = "IMPOSTAZIONI",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            SettingsItem(
                icon = Icons.Default.History,
                label = "Cancella cronologia",
                onClick = { showClearHistoryDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.FavoriteBorder,
                label = "Cancella preferiti",
                onClick = { showClearFavoritesDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.SkipNext,
                label = "Auto-play episodio",
                onClick = { viewModel.toggleAutoPlayNextEpisode() },
                trailing = {
                    Switch(
                        checked = autoPlayNextEpisode,
                        onCheckedChange = { viewModel.toggleAutoPlayNextEpisode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = VixRed,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    )
                }
            )

            SettingsItem(
                icon = Icons.Default.Lock,
                label = if (altadefCookie.isNotBlank()) "Altadefinizione \u2705 Connesso" else "Login Altadefinizione",
                onClick = { showAltadefAuth = true }
            )

            if (altadefCookie.isNotBlank()) {
                SettingsItem(
                    icon = Icons.Default.Share,
                    label = "Invia cookie a TV",
                    onClick = { showSendToTv = true }
                )
            }

            SettingsItem(
                icon = Icons.Default.Info,
                label = "Info app",
                onClick = { showInfoDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.SystemUpdate,
                label = "Controlla aggiornamenti",
                onClick = { viewModel.checkForUpdate() }
            )
        }
    }

    if (showAltadefAuth) {
        AltadefinizioneAuthScreen(
            onCookieExtracted = { cookie ->
                if (cookie.isNotBlank()) {
                    AppSettings.setAltadefinizioneCookie(context, cookie)
                    altadefCookie = cookie
                }
                showAltadefAuth = false
            },
            onDismiss = { showAltadefAuth = false }
        )
    }

    if (showSendToTv) {
        SendToTvDialog(cookie = altadefCookie, onDismiss = { showSendToTv = false })
    }
}

@Composable
private fun ProfileEditDialog(
    title: String,
    initialName: String = "",
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, email: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F1F1F),
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome profilo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedLabelColor = VixRed,
                        focusedBorderColor = VixRed,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = VixRed,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (opzionale)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedLabelColor = VixRed,
                        focusedBorderColor = VixRed,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = VixRed,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), email.trim()) }) {
                Text("Salva", color = VixRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (trailing != null) {
                trailing()
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SendToTvDialog(cookie: String, onDismiss: () -> Unit) {
    var tvIp by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("idle") }
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F).copy(alpha = 0.95f))
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(24.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Invia cookie a TV",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (status == "sent") {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    )
                    Text("Cookie inviato!", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = VixRed), shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("OK")
                    }
                } else {
                    Text("Inserisci IP e codice mostrati sulla TV.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))
                    OutlinedTextField(
                        value = tvIp, onValueChange = { tvIp = it },
                        label = { Text("IP TV (es. 192.168.1.5:8765)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = VixRed, focusedBorderColor = VixRed, unfocusedBorderColor = Color.Gray, cursorColor = VixRed),
                        enabled = status != "sending"
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pairingCode, onValueChange = { pairingCode = it },
                        label = { Text("Codice a 6 cifre") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = VixRed, focusedBorderColor = VixRed, unfocusedBorderColor = Color.Gray, cursorColor = VixRed),
                        enabled = status != "sending"
                    )
                    if (status == "error") {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg, color = Color(0xFFEF5350), fontSize = 12.sp)
                    }
                    if (status == "sending") {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(color = VixRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Invio...", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Annulla") }
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    status = "sending"; errorMsg = ""
                                    try {
                                        val host = tvIp.trim().removeSuffix("/")
                                        val urlStr = if (host.contains(":")) "http://$host" else "http://$host:$PAIRING_PORT"
                                        val url = URL(urlStr)
                                        val conn = url.openConnection() as java.net.HttpURLConnection
                                        conn.requestMethod = "POST"; conn.doOutput = true
                                        conn.connectTimeout = 5000; conn.readTimeout = 5000
                                        conn.setRequestProperty("Content-Type", "application/json")
                                        conn.outputStream.use { it.write("""{"code":"$pairingCode","cookie":"$cookie"}""".toByteArray()) }
                                        if (conn.responseCode == 200) status = "sent"
                                        else { status = "error"; errorMsg = "Codice errato (HTTP ${conn.responseCode})" }
                                    } catch (e: Exception) { status = "error"; errorMsg = "Errore: ${e.message?.take(60) ?: "rete"}" }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = status != "sending" && tvIp.isNotBlank() && pairingCode.length == 6,
                            colors = ButtonDefaults.buttonColors(containerColor = VixRed),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Invia") }
                    }
                }
            }
        }
    }
}
