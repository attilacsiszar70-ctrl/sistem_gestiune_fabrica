package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrepturiUtilizatoriScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    var listaUtilizatori by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    // Stări pentru Formularul de Adăugare Utilizator Nou
    var extindeFormularNou by remember { mutableStateOf(false) }
    var nouNumeText by remember { mutableStateOf("") }
    var nouUsernameText by remember { mutableStateOf("") }
    var nouPinText by remember { mutableStateOf("") }
    var nouaFunctieText by remember { mutableStateOf("") }

    var statusMesajText by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(false) }

    // 💡 CONECTAT LA COLECȚIA CORECTĂ: Ascultăm live de utilizatori_sistem
    LaunchedEffect(Unit) {
        firestore.collection("utilizatori_sistem").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaUtilizatori = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. BARĂ TITLU ȘI NAVIGARE ---
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reglare Drepturi",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FilledTonalIconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Înapoi",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text("Configurarea accesului securizat pe ferestre și ateliere", fontSize = 13.sp, color = Color.Gray)
                Divider(color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
            }
        }

        // --- 2. FORMULAR OPERATOR NOU ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (extindeFormularNou) Color(0xFFE65100) else Color(0xFFE5E5E5), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = if (extindeFormularNou) Color.White else Color(0xFFF8F9FA))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { extindeFormularNou = !extindeFormularNou }
                        .padding(16.dp)
                ) {
                    Text(
                        text = "✨ ADAUGĂ OPERATOR NOU ÎN FABRICĂ",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = if (extindeFormularNou) Color(0xFFE65100) else Color(0xFF495057)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (extindeFormularNou) "Apasă pentru a ascunde formularul ▲" else "Apasă pentru a deschide formularul ▼",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )

                    AnimatedVisibility(visible = extindeFormularNou) {
                        Column(modifier = Modifier.padding(top = 16.dp).clickable(enabled = false) {}) {
                            OutlinedTextField(value = nouNumeText, onValueChange = { nouNumeText = it }, label = { Text("Nume și Prenume Complet") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(value = nouUsernameText, onValueChange = { nouUsernameText = it }, label = { Text("Nume Utilizator Login (Fără spații)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(value = nouPinText, onValueChange = { nouPinText = it }, label = { Text("Cod PIN Numeric") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(value = nouaFunctieText, onValueChange = { nouaFunctieText = it }, label = { Text("Funcție / Departament") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val numeS = nouNumeText.trim()
                                    val userS = nouUsernameText.trim()
                                    val pinS = nouPinText.trim()
                                    val functieS = nouaFunctieText.trim()

                                    if (numeS.isEmpty() || userS.isEmpty() || pinS.isEmpty() || functieS.isEmpty()) {
                                        statusMesajText = "Eroare: Completați toate câmpurile!"; return@Button
                                    }
                                    seIncarca = true

                                    val structuraUtilizatorNou = mapOf(
                                        "nume" to numeS,
                                        "username" to userS,
                                        "pin" to pinS,
                                        "functie" to functieS,
                                        "drepturiFerestre" to mapOf(
                                            "administrare_drepturi" to false,
                                            "lansare_planuri" to false,
                                            "stocuri" to false,
                                            "retete_nomenclator" to false,
                                            "rapoarte_analiza" to false,
                                            "dashboard" to false,
                                            "gestiune_cerneluri" to false
                                        ),
                                        "drepturiAteliere" to mapOf(
                                            "Atelier0" to false,
                                            "Atelier1" to false,
                                            "Atelier2" to false,
                                            "Atelier3" to false,
                                            "Atelier4" to false
                                        )
                                    )

                                    firestore.collection("utilizatori_sistem").document(userS.lowercase())
                                        .set(structuraUtilizatorNou, SetOptions.merge())
                                        .addOnSuccessListener {
                                            seIncarca = false; extindeFormularNou = false
                                            nouNumeText = ""; nouUsernameText = ""; nouPinText = ""; nouaFunctieText = ""
                                            statusMesajText = "Succes! Operatorul '$numeS' a fost înregistrat!"
                                        }
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                enabled = !seIncarca,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                            ) { Text("SALVEAZĂ OPERATORUL NOU ÎN CLOUD 💾", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        // --- 3. MEDIU AFISARE STATUS MESAJ ---
        if (statusMesajText.isNotEmpty()) {
            item {
                Text(
                    text = statusMesajText,
                    color = Color(0xFF198754),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- 4. LISTARE OPERATORI EXISTENȚI ---
        items(listaUtilizatori) { user ->
            val uIdDoc = user["idDoc"]?.toString() ?: ""
            val uNume = user["nume"]?.toString() ?: user["username"]?.toString() ?: "Operator"
            val uFunctie = user["functie"]?.toString() ?: "Nespecificată"
// 💡 CORECTAT DEFINITIV: Structura as? Map<*, > corectă fără caractere lipsă
            // 💡 CORECTAT: Folosim structura standard <*, *> pentru ambele hărți de permisiuni
            val drepturiFerestre = user["drepturiFerestre"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val drepturiAteliere = user["drepturiAteliere"] as? Map<*, *> ?: emptyMap<Any, Any>()

            val areReglareDrepturi = drepturiFerestre["administrare_drepturi"] == true || drepturiFerestre["administrare_drepturi"]?.toString() == "true"
            val areLansarePlanize = drepturiFerestre["lansare_planuri"] == true || drepturiFerestre["lansare_planuri"]?.toString() == "true"
            val areMonitorizareStocuri = drepturiFerestre["stocuri"] == true || drepturiFerestre["stocuri"]?.toString() == "true"
            val areReteteNomenclator = drepturiFerestre["retete_nomenclator"] == true || drepturiFerestre["retete_nomenclator"]?.toString() == "true"
            val areRaportareSaptamana = drepturiFerestre["rapoarte_analiza"] == true || drepturiFerestre["rapoarte_analiza"]?.toString() == "true"
            val areDashboardMastri = drepturiFerestre["dashboard"] == true || drepturiFerestre["dashboard"]?.toString() == "true"
            val areGestiuneCerneluri = drepturiFerestre["gestiune_cerneluri"] == true || drepturiFerestre["gestiune_cerneluri"]?.toString() == "true"
            val areAt0 = drepturiAteliere["Atelier0"] == true || drepturiAteliere["Atelier0"]?.toString() == "true"
            val areAt1 = drepturiAteliere["Atelier1"] == true || drepturiAteliere["Atelier1"]?.toString() == "true"
            val areAt2 = drepturiAteliere["Atelier2"] == true || drepturiAteliere["Atelier2"]?.toString() == "true"
            val areAt3 = drepturiAteliere["Atelier3"] == true || drepturiAteliere["Atelier3"]?.toString() == "true"
            val areAt4 = drepturiAteliere["Atelier4"] == true || drepturiAteliere["Atelier4"]?.toString() == "true"
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = uNume.uppercase(), fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Text(text = "Funcție / Departament: $uFunctie", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.LightGray.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "🛡️ ACCES FERESTRE LOGISTICĂ ȘI MODULE MANAGEMENT:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = areReglareDrepturi, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiFerestre", "administrare_drepturi", b) { statusMesajText = "Drept salvat pentru $uNume! 🛡️" } })
                        Text("Reglare Drepturi Operatori", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = areLansarePlanize, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiFerestre", "lansare_planuri", b) { statusMesajText = "Drept salvat pentru $uNume! 🚀" } })
                        Text("Lansare Logistică Planuri 🚀", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = areMonitorizareStocuri, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiFerestre", "stocuri", b) { statusMesajText = "Drept salvat pentru $uNume! 🪵" } })
                        Text("Monitorizare Globală Stocuri 🪵", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = areReteteNomenclator, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiFerestre", "retete_nomenclator", b) { statusMesajText = "Drept salvat pentru $uNume! 📋" } })
                        Text("Configurator Rețete Nomenclator (BOM 📋)", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = areRaportareSaptamana, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiFerestre", "rapoarte_analiza", b) { statusMesajText = "Drept salvat pentru $uNume! 📊" } })
                        Text("Rapoartare Producție Istorice (Săptămână / Lună 📊)", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = areDashboardMastri, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiFerestre", "dashboard", b) { statusMesajText = "Drept salvat pentru $uNume! 🔍" } })
                        Text("Analiză Predictivă (Panou Maiștri 🔍)", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = areGestiuneCerneluri, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiFerestre", "gestiune_cerneluri", b) { statusMesajText = "Drept salvat pentru $uNume! 🎨" } })
                        Text("Gestiune Cerneluri Imprimare (Yellow/Magenta/Cyan/Black 🎨)", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "🔨 PERMISIUNI LIVE RAPORTARE SCHIMBĂRI PE SECȚIE:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = areAt0, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiAteliere", "Atelier0", b) {} })
                            Text("AT0", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = areAt1, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiAteliere", "Atelier1", b) {} })
                            Text("AT1", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = areAt2, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiAteliere", "Atelier2", b) {} })
                            Text("AT2", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = areAt3, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiAteliere", "Atelier3", b) {} })
                            Text("AT3", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = areAt4, onCheckedChange = { b -> actualizeazaDreptInCloud(firestore, uIdDoc, "drepturiAteliere", "Atelier4", b) {} })
                            Text("AT4", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
// 💡 CONECTAT LA COLECȚIA CORECTĂ: Modificările se duc direct în utilizatori_sistem
fun actualizeazaDreptInCloud(
    firestore: FirebaseFirestore,
    documentId: String,
    hartaNume: String,
    cheieDrept: String,
    valoareNoua: Boolean,
    onSuccess: () -> Unit
) {
    if (documentId.isEmpty()) return
    val caleActualizareMap = mapOf("$hartaNume.$cheieDrept" to valoareNoua)
    firestore.collection("utilizatori_sistem").document(documentId)
        .update(caleActualizareMap)
        .addOnSuccessListener { onSuccess() }
}