package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(firestore: FirebaseFirestore, onLoginSuccess: (userData: Map<String, Any>) -> Unit) {
    var usernameSelectat by remember { mutableStateOf("") }
    var pinIntrodus by remember { mutableStateOf("") }
    var listaUtilizatori by remember { mutableStateOf<List<String>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var mesajEroare by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        firestore.collection("utilizatori").get().addOnSuccessListener { result: QuerySnapshot ->
            listaUtilizatori = result.documents.map { it.getString("username") ?: "" }.filter { it.isNotEmpty() }
        }
    }

    Row(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
            Text(text = "SISTEM GESTIUNE FABRICĂ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = if (usernameSelectat.isEmpty()) "Alege numele tău din listă..." else usernameSelectat,
                    onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listaUtilizatori.forEach { ut -> DropdownMenuItem(text = { Text(ut, fontSize = 18.sp) }, onClick = { usernameSelectat = ut; expanded = false; mesajEroare = "" }) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index -> Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(if (index < pinIntrodus.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
            }
            if (mesajEroare.isNotEmpty()) Text(text = mesajEroare, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            if (seIncarca) CircularProgressIndicator()
        }
        Spacer(modifier = Modifier.width(48.dp))
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            val taste = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Șterge", "0", "OK")
            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(320.dp)) {
                items(taste) { tasta ->
                    val esteActiune = tasta == "Șterge" || tasta == "OK"
                    Button(
                        onClick = {
                            when (tasta) {
                                "Șterge" -> if (pinIntrodus.isNotEmpty()) pinIntrodus = pinIntrodus.dropLast(1)
                                "OK" -> {
                                    if (usernameSelectat.isEmpty() || pinIntrodus.length < 4) { mesajEroare = "PIN incomplet!"; return@Button }
                                    seIncarca = true
                                    firestore.collection("utilizatori").whereEqualTo("username", usernameSelectat).whereEqualTo("pin", pinIntrodus).get().addOnSuccessListener { querySnapshot ->
                                        seIncarca = false
                                        if (!querySnapshot.isEmpty) onLoginSuccess(querySnapshot.documents.first().data ?: emptyMap()) else { mesajEroare = "PIN incorect!"; pinIntrodus = "" }
                                    }.addOnFailureListener { seIncarca = false; mesajEroare = "Eroare rețea!" }
                                }
                                else -> if (pinIntrodus.length < 4) pinIntrodus += tasta
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (esteActiune) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (esteActiune) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer),
                        shape = CircleShape, modifier = Modifier.size(80.dp).padding(2.dp)
                    ) { Text(text = tasta, fontSize = if (esteActiune) 14.sp else 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}
