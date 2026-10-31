package com.example.sistem_gestiune_fabrica.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    firestore: FirebaseFirestore,
    onLoginSuccess: (userData: Map<String, Any>) -> Unit
) {
    var usernameSelectat by remember { mutableStateOf("") }
    var pinIntrodus by remember { mutableStateOf("") }
    var listaUtilizatori by remember { mutableStateOf<List<String>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var mesajEroare by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(false) }

    // Încărcăm lista de utilizatori din Firebase Firestore la deschiderea ecranului
    LaunchedEffect(Unit) {
        firestore.collection("utilizatori")
            .get()
            .addOnSuccessListener { result: QuerySnapshot ->
                listaUtilizatori = result.documents.map { it.getString("username") ?: "" }.filter { it.isNotEmpty() }
            }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // COLOANA STÂNGA: Selectare Utilizator și Mesaje
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "SISTEM GESTIUNE FABRICĂ",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Producție Scaune v1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            Text(text = "Operator:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

            // Selector Dropdown mare pentru muncitori
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = if (usernameSelectat.isEmpty()) "Alege numele tău din listă..." else usernameSelectat,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )


                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    listaUtilizatori.forEach { ut ->
                        DropdownMenuItem(
                            text = { Text(ut, fontSize = 18.sp) },
                            onClick = {
                                usernameSelectat = ut
                                expanded = false
                                mesajEroare = ""
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Indicator vizual pentru PIN (Cerculețe ca la telefon)
            Text(text = "Cod PIN introdus:", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) { index ->
                    val activ = index < pinIntrodus.length
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (activ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            if (mesajEroare.isNotEmpty()) {
                Text(
                    text = mesajEroare,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (seIncarca) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
        }

        Spacer(modifier = Modifier.width(48.dp))

        // COLOANA DREAPTĂ: NUMPAD DIGITAL INDUSTRIAL (Taste mari pentru ecran tactil)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val taste = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Șterge", "0", "OK")

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(320.dp)
            ) {
                items(taste) { tasta ->
                    val esteActiune = tasta == "Șterge" || tasta == "OK"
                    Button(
                        onClick = {
                            mesajEroare = ""
                            when (tasta) {
                                "Șterge" -> {
                                    if (pinIntrodus.isNotEmpty()) pinIntrodus = pinIntrodus.dropLast(1)
                                }
                                "OK" -> {
                                    if (usernameSelectat.isEmpty() || pinIntrodus.length < 4) {
                                        mesajEroare = "Selectați operatorul și introduceți PIN-ul de 4 cifre!"
                                        return@Button
                                    }

                                    seIncarca = true
                                    firestore.collection("utilizatori")
                                        .whereEqualTo("username", usernameSelectat)
                                        .whereEqualTo("pin", pinIntrodus)
                                        .get()
                                        .addOnSuccessListener { querySnapshot ->
                                            seIncarca = false
                                            if (!querySnapshot.isEmpty) {
                                                val dateUtilizator = querySnapshot.documents.first().data ?: emptyMap()
                                                onLoginSuccess(dateUtilizator)
                                            } else {
                                                mesajEroare = "Cod PIN incorect! Încercați din nou."
                                                pinIntrodus = ""
                                            }
                                        }
                                        .addOnFailureListener {
                                            seIncarca = false
                                            mesajEroare = "Eroare rețea/bază de date!"
                                        }
                                }
                                else -> {
                                    if (pinIntrodus.length < 4) {
                                        pinIntrodus += tasta
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (esteActiune) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (esteActiune) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer
                        ),

                        shape = CircleShape,
                        modifier = Modifier
                            .size(80.dp)
                            .padding(2.dp)
                    ) {
                        Text(
                            text = tasta,
                            fontSize = if (esteActiune) 14.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
