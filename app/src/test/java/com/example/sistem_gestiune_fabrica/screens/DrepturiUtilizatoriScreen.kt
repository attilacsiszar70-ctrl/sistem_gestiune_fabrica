package com.example.sistem_gestiune_fabrica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrepturiUtilizatoriScreen(firestore: FirebaseFirestore, onBackClick: () -> Unit) {
    var listaUtilizatori by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var utilizatorSelectat by remember { mutableStateOf<Map<String, Any>?>(null) }
    var mesajStatus by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        firestore.collection("utilizatori")
            .addSnapshotListener { snapshot: QuerySnapshot?, _ ->
                if (snapshot != null) {
                    listaUtilizatori = snapshot.documents.map { doc: DocumentSnapshot ->
                        val data = doc.data ?: emptyMap()
                        data + ("idDocument" to doc.id)
                    }
                }
            }
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text("Selectează Operator", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn {
                items(listaUtilizatori) { ut ->
                    val esteSelectat = utilizatorSelectat != null && utilizatorSelectat!!["idDocument"] == ut["idDocument"]

                    Button(
                        onClick = { utilizatorSelectat = ut; mesajStatus = "" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (esteSelectat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) { Text(text = ut["nume"].toString()) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) { Text("Înapoi") }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
            if (utilizatorSelectat != null) {
                val currentUt = utilizatorSelectat!!
                val idDoc = currentUt["idDocument"].toString()

                val drepturiFerestre = remember(idDoc) {
                    mutableStateMapOf<String, Boolean>().apply {
                        val map = currentUt["drepturiFerestre"] as? Map<*, *>
                        put("dashboard", map?.get("dashboard") as? Boolean ?: false)
                        put("stocuri", map?.get("stocuri") as? Boolean ?: false)
                        put("retete_nomenclator", map?.get("retete_nomenclator") as? Boolean ?: false)
                        put("administrare_drepturi", map?.get("administrare_drepturi") as? Boolean ?: false)
                    }
                }

                val drepturiAteliere = remember(idDoc) {
                    mutableStateMapOf<String, Boolean>().apply {
                        val map = currentUt["drepturiAteliere"] as? Map<*, *>
                        put("Atelier1", map?.get("Atelier1") as? Boolean ?: false)
                        put("Atelier2", map?.get("Atelier2") as? Boolean ?: false)
                        put("Atelier3", map?.get("Atelier3") as? Boolean ?: false)
                        put("Atelier4", map?.get("Atelier4") as? Boolean ?: false)
                    }
                }

                Text(text = "Drepturi: ${currentUt["nume"]}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
                    item { Text("ACCES FERESTRE:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    drepturiFerestre.keys.forEach { fereastra ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = drepturiFerestre[fereastra] == true, onCheckedChange = { checkboxState -> drepturiFerestre[fereastra] = checkboxState })
                                Text(text = fereastra)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item { Text("DREPT ATELIERE:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    drepturiAteliere.keys.forEach { atelier ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = drepturiAteliere[atelier] == true, onCheckedChange = { checkboxState -> drepturiAteliere[atelier] = checkboxState })
                                Text(text = atelier)
                            }
                        }
                    }
                }

                if (mesajStatus.isNotEmpty()) { Text(text = mesajStatus, color = MaterialTheme.colorScheme.primary) }

                Button(onClick = {
                    val updateDate = mapOf("drepturiFerestre" to drepturiFerestre.toMap(), "drepturiAteliere" to drepturiAteliere.toMap())
                    firestore.collection("utilizatori").document(idDoc).update(updateDate)
                        .addOnSuccessListener { mesajStatus = "Actualizat cu succes!" }
                        .addOnFailureListener { mesajStatus = "Eroare la actualizare." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Salvează") }
            } else {
                Text("Selectați un operator din stânga.")
            }
        }
    }
}
