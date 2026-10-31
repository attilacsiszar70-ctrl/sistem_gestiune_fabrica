package com.example.sistem_gestiune_fabrica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomenclatorBomScreen(firestore: FirebaseFirestore, onBackClick: () -> Unit) {
    var denumireScaun by remember { mutableStateOf("") }
    var codScaun by remember { mutableStateOf("") }
    val componenteReteta = remember { mutableStateListOf<Pair<String, Int>>() }
    var repereDisponibile by remember { mutableStateOf<List<String>>(emptyList()) }
    var expandedDropdownIndex by remember { mutableStateOf<Int?>(null) }
    var mesajStatus by remember { mutableStateOf("") }
    var esteEroare by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        firestore.collection("nomenclator_repere")
            .get()
            .addOnSuccessListener { result ->
                repereDisponibile = result.documents.map { it.id }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Configurare Produs Nou & Rețetă (BOM)", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(value = denumireScaun, onValueChange = { denumireScaun = it }, label = { Text("Denumire Model Scaun") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = codScaun, onValueChange = { codScaun = it.uppercase() }, label = { Text("Cod Unic Produs / SKU") }, modifier = Modifier.fillMaxWidth())

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Componente Rețetă:", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { componenteReteta.add(Pair("", 1)) }) { Text("+ Adaugă Reper") }
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            itemsIndexed(componenteReteta) { index, item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1.5f)) {
                            OutlinedButton(onClick = { expandedDropdownIndex = index }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (item.first.isEmpty()) "Alege Reper" else item.first)
                            }
                            DropdownMenu(expanded = expandedDropdownIndex == index, onDismissRequest = { expandedDropdownIndex = null }) {
                                repereDisponibile.forEach { reper ->
                                    DropdownMenuItem(text = { Text(reper) }, onClick = {
                                        componenteReteta[index] = Pair(reper, item.second)
                                        expandedDropdownIndex = null
                                    })
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = item.second.toString(),
                            onValueChange = { qty -> componenteReteta[index] = Pair(item.first, qty.toIntOrNull() ?: 0) },
                            label = { Text("Cant.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.7f)
                        )
                        IconButton(onClick = { componenteReteta.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Șterge", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (mesajStatus.isNotEmpty()) {
            Text(text = mesajStatus, color = if (esteEroare) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBackClick, modifier = Modifier.weight(1f)) { Text("Înapoi") }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                if (denumireScaun.isEmpty() || codScaun.isEmpty() || componenteReteta.isEmpty()) {
                    mesajStatus = "Toate câmpurile sunt obligatorii!"
                    esteEroare = true
                    return@Button
                }
                val dateBom = componenteReteta.map { mapOf("reperId" to it.first, "cantitateNecesara" to it.second) }
                val produsNou = mapOf("denumire" to denumireScaun, "retetaBOM" to dateBom)

                firestore.collection("nomenclator_produse").document(codScaun).set(produsNou)
                    .addOnSuccessListener {
                        mesajStatus = "Salvare reușită!"
                        esteEroare = false
                        denumireScaun = ""; codScaun = ""; componenteReteta.clear()
                    }
                    .addOnFailureListener { mesajStatus = "Eroare la salvare."; esteEroare = true }
            }, modifier = Modifier.weight(1f)) { Text("Salvează BOM") }
        }
    }
}
