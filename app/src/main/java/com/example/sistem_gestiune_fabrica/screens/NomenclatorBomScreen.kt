package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.math.BigDecimal

private fun formateazaNumarFaraNotatieStiintifica(valoare: Any?): String {
    if (valoare == null) return ""
    return when (valoare) {
        is Number -> BigDecimal.valueOf(valoare.toDouble()).stripTrailingZeros().toPlainString()
        is String -> {
            val d = valoare.replace(",", ".").toDoubleOrNull()
            if (d != null) BigDecimal.valueOf(d).stripTrailingZeros().toPlainString() else valoare
        }
        else -> valoare.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomenclatorBomScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    var listaModeleScaune by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var modelSelectatPtReteta by remember { mutableStateOf<Map<String, Any>?>(null) }

    var extindeProdusNou by remember { mutableStateOf(false) }
    var nouModelId by remember { mutableStateOf("") }
    var nouModelNume by remember { mutableStateOf("") }

    var extindeClonare by remember { mutableStateOf(false) }
    var clonatModelId by remember { mutableStateOf("") }
    var clonatModelNume by remember { mutableStateOf("") }

    var IDReperText by remember { mutableStateOf("") }
    var denumirePiesaText by remember { mutableStateOf("") }
    var cantitateNecesaraText by remember { mutableStateOf("") }
    var normaConsumText by remember { mutableStateOf("") }

    // Stări noi pentru gestionarea mai multor subansamble
    var subansambluDestinatieText by remember { mutableStateOf("") }
    var cantitateSubansambluText by remember { mutableStateOf("1") }
    var hartaSubansambleNomenclator by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var fazaPrelucrareText by remember { mutableStateOf("") }
    var listaFazeNomenclator by remember { mutableStateOf<List<String>>(emptyList()) }

    // Stări pentru Consumabile & Auxiliare asociate
    var consumabilNumeText by remember { mutableStateOf("") }
    var consumabilNormaText by remember { mutableStateOf("") }
    var consumabilUnitateText by remember { mutableStateOf("kg") }
    var listaConsumabileNomenclator by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    val optiuniAteliere = listOf("Atelier0", "Atelier1", "Atelier2", "Atelier3", "Atelier4")
    var atelierSelectatAcum by remember { mutableStateOf("Atelier1") }

    var esteModEditarePiesa by remember { mutableStateOf(false) }
    var indexPiesaEditata by remember { mutableStateOf(-1) }

    var statusMesajText by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").document("semifabricate_comune").get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    val structuraComuneInit = mapOf(
                        "modelId" to "semifabricate_comune",
                        "modelNume" to "🪵 SEMIFABRICATE",
                        "cantitateScaune" to 0,
                        "stare" to "In Lucru",
                        "retetaBOM" to listOf<Any>()
                    )
                    firestore.collection("comenzi_productie").document("semifabricate_comune").set(structuraComuneInit, SetOptions.merge())
                }
            }

        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaModeleScaune = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. BARĂ TITLU ȘI NAVIGARE ---
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Configurator Rețete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Definire structură piese și ateliere", fontSize = 12.sp, color = Color.Gray)
                }
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
        }

        // --- 2. MODUL GENERARE MODEL NOU DE LA ZERO ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, if (extindeProdusNou) Color(0xFF2E7D32) else Color(0xFFE5E5E5), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = if (extindeProdusNou) Color.White else Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().clickable { extindeProdusNou = !extindeProdusNou; extindeClonare = false }) {
                        Text(text = "✨ GENEREAZĂ REȚETĂ PENTRU MODEL NOU (GOL)", fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (extindeProdusNou) Color(0xFF2E7D32) else Color(0xFF495057))
                        Text(text = if (extindeProdusNou) "Ascunde ▲" else "Deschide Formular ▼", fontSize = 11.sp, color = Color.Gray)
                    }

                    AnimatedVisibility(visible = extindeProdusNou) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            OutlinedTextField(value = nouModelNume, onValueChange = { nouModelNume = it }, label = { Text("Denumire Model Scaun") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(value = nouModelId, onValueChange = { nouModelId = it }, label = { Text("ID Cod Model Unic (Ex: tonstad_oak_5)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (nouModelNume.trim().isEmpty() || nouModelId.trim().isEmpty()) { statusMesajText = "Completați ambele câmpuri!"; return@Button }
                                    seIncarca = true
                                    val structuraScaunNouMap = mapOf("modelId" to nouModelId.trim().lowercase(), "modelNume" to nouModelNume.trim(), "cantitateScaune" to 0, "stare" to "In Asteptare", "retetaBOM" to listOf<Any>())
                                    firestore.collection("comenzi_productie").document(nouModelId.trim().lowercase()).set(structuraScaunNouMap, SetOptions.merge())
                                        .addOnSuccessListener { seIncarca = false; extindeProdusNou = false; nouModelNume = ""; nouModelId = ""; statusMesajText = "Modelul nou a fost adăugat!" }
                                        .addOnFailureListener { seIncarca = false; statusMesajText = "Eroare rețea: Nu s-a putut crea produsul." }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp), enabled = !seIncarca, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) { Text("CREEAZĂ PRODUS NOU ÎN CLOUD 💾", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        // --- 3. SELECTARE ȘI MODUL INTEGRAT DE CLONARE PRODUS ---
        item {
            Text("2. Selectați Modelul Sursă pentru Rețetă:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 2 // 💡 FORȚEAZĂ AFIȘAREA PE FIX 2 COLOANE
                ) {
                    listaModeleScaune.distinctBy { it["modelId"]?.toString() }.forEach { model ->
                        val mNume = model["modelNume"]?.toString() ?: "Scaun"
                        val esteSelectat = modelSelectatPtReteta?.get("modelId")?.toString() == model["modelId"]?.toString()

                        // Adăugăm Modifier.weight(1f) pe fiecare cip pentru ca ambele coloane să fie egale ca lățime
                        Box(modifier = Modifier.weight(1f)) {
                            FilterChip(
                                selected = esteSelectat,
                                onClick = { modelSelectatPtReteta = model; IDReperText = ""; denumirePiesaText = ""; cantitateNecesaraText = ""; subansambluDestinatieText = ""; extindeClonare = false; esteModEditarePiesa = false; statusMesajText = "" },
                                label = {
                                    Text(
                                        mNume.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth() // Centrează textul în interiorul butonului
                                    )
                                },
                                modifier = Modifier.fillMaxWidth() // Întinde butonul pe toată jumătatea de coloană
                            )
                        }
                    }

                    // Truc vizual: Dacă numărul total de produse este impar, adăugăm un spațiu gol
                    // pe cealaltă jumătate pentru ca ultimul buton să nu se lăbească pe tot ecranul
                    if (listaModeleScaune.distinctBy { it["modelId"]?.toString() }.size % 2 != 0) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                modelSelectatPtReteta?.let { modelActiv ->
                    val idDocModelDeSters = modelActiv["idDoc"]?.toString() ?: ""
                    val mNumeCurent = modelActiv["modelNume"]?.toString() ?: "Scaun"
                    val retetaBOMDeCopiat = modelActiv["retetaBOM"] as? List<*> ?: listOf<Any>()

                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF0D6EFD).copy(alpha = 0.5f), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8))) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Column(modifier = Modifier.fillMaxWidth().clickable { extindeClonare = !extindeClonare; extindeProdusNou = false }) {
                                Text(text = "👯‍♂️ DUPLICĂ TOATĂ STRUCTURA CURENTĂ PENTRU ALT MODEL", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0D6EFD))
                                Text(text = if (extindeClonare) "Ascunde ▲" else "Deschide formularul de clonare ▼", fontSize = 11.sp, color = Color.Gray)
                            }
                            AnimatedVisibility(visible = extindeClonare) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    OutlinedTextField(value = clonatModelNume, onValueChange = { clonatModelNume = it }, label = { Text("Nume model duplicat") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(value = clonatModelId, onValueChange = { clonatModelId = it }, label = { Text("Cod ID model duplicat") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            if (clonatModelNume.trim().isEmpty() || clonatModelId.trim().isEmpty()) { statusMesajText = "Completați ambele câmpuri!"; return@Button }
                                            seIncarca = true
                                            val structuraClonataMap = mapOf("modelId" to clonatModelId.trim().lowercase(), "modelNume" to clonatModelNume.trim(), "cantitateScaune" to 100, "stare" to "In Lucru", "retetaBOM" to retetaBOMDeCopiat)
                                            firestore.collection("comenzi_productie").document(clonatModelId.trim().lowercase()).set(structuraClonataMap, SetOptions.merge())
                                                .addOnSuccessListener { seIncarca = false; extindeClonare = false; clonatModelNume = ""; clonatModelId = ""; statusMesajText = "Modelul a fost clonat live!" }
                                                .addOnFailureListener { seIncarca = false; statusMesajText = "Eroare rețea la clonare." }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(44.dp), enabled = !seIncarca, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD))
                                    ) { Text("REPLICĂ ACUM TOATE REPERELE IN CLOUD 🚀", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (idDocModelDeSters.isEmpty()) return@Button
                            seIncarca = true
                            firestore.collection("comenzi_productie").document(idDocModelDeSters).delete().addOnSuccessListener { seIncarca = false; modelSelectatPtReteta = null; statusMesajText = "Produsul '$mNumeCurent' a fost eliminat definitiv!" }
                                .addOnFailureListener { seIncarca = false; statusMesajText = "Eroare rețea la ștergere." }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(42.dp), enabled = !seIncarca
                    ) { Text("🗑️ ȘTERGE DEFINITIV PRODUSUL SELECTAT", fontSize = 12.sp, fontWeight = FontWeight.Black) }
                }
            }
        }
// --- 4. FORMULAR COMPONENTE REPERE (MODIFICAT PENTRU SUBANSAMBLE MULTIPLE) ---
        item {
            modelSelectatPtReteta?.let { modelActiv ->
                Card(modifier = Modifier.fillMaxWidth().border(1.dp, if (esteModEditarePiesa) Color(0xFF0D6EFD) else Color(0xFFE5E5E5), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = if (esteModEditarePiesa) "✏️ EDITARE REPER ÎN REȚETĂ" else "🧱 ADĂUGARE REPER NOU ÎN REȚETĂ", fontWeight = FontWeight.Black, color = if (esteModEditarePiesa) Color(0xFF0D6EFD) else MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = IDReperText, onValueChange = { IDReperText = it }, label = { Text("ID Unic Reper") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !esteModEditarePiesa)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(value = denumirePiesaText, onValueChange = { denumirePiesaText = it }, label = { Text("Denumire Piesă Comercială") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(value = cantitateNecesaraText, onValueChange = { cantitateNecesaraText = it }, label = { Text("Cantitate necesară per scaun") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(value = normaConsumText, onValueChange = { normaConsumText = it }, label = { Text("Normă consum (mp/săptămână)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(modifier = Modifier.height(10.dp))
// ZONĂ NOUĂ: Adăugare Subansamble Multiple cu Cantitate Specifică per Subansamblu
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = subansambluDestinatieText,
                                onValueChange = { subansambluDestinatieText = it },
                                label = { Text("Subansamblu Destinație (ex: agregat fata)") },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = cantitateSubansambluText,
                                onValueChange = { cantitateSubansambluText = it },
                                label = { Text("Buc/Sub") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.8f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val sb = subansambluDestinatieText.trim().lowercase()
                                    val qSub = cantitateSubansambluText.toIntOrNull() ?: 1
                                    if (sb.isNotEmpty()) {
                                        val nouaHarta = hartaSubansambleNomenclator.toMutableMap()
                                        nouaHarta[sb] = qSub
                                        hartaSubansambleNomenclator = nouaHarta
                                        subansambluDestinatieText = ""
                                        cantitateSubansambluText = "1"
                                        // Actualizăm automat cantitatea necesară per scaun ca sumă a subansamblelor dacă utilizatorul dorește
                                        val suma = nouaHarta.values.sum()
                                        if (suma > 0) {
                                            cantitateNecesaraText = suma.toString()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("+ ASOCIAZĂ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (hartaSubansambleNomenclator.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Subansamble asociate & cantitate per subansamblu (apasă pentru ștergere):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                hartaSubansambleNomenclator.forEach { (subNume, subCant) ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            val nouaHarta = hartaSubansambleNomenclator.toMutableMap()
                                            nouaHarta.remove(subNume)
                                            hartaSubansambleNomenclator = nouaHarta
                                            val suma = nouaHarta.values.sum()
                                            if (suma > 0) cantitateNecesaraText = suma.toString()
                                        },
                                        label = { Text("🪵 $subNume ($subCant buc) ✕", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
// Zonă Adăugare Faze
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = fazaPrelucrareText,
                                onValueChange = { fazaPrelucrareText = it },
                                label = { Text("Adaugă Fază Prelucrare (ex: Frezare CNC)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val fz = fazaPrelucrareText.trim()
                                    if (fz.isNotEmpty() && !listaFazeNomenclator.contains(fz)) {
                                        listaFazeNomenclator = listaFazeNomenclator + fz
                                        fazaPrelucrareText = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("+ FAZĂ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (listaFazeNomenclator.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Fazele adăugate pe acest reper (apasă pentru ștergere):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listaFazeNomenclator.forEachIndexed { fIdx, fItem ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { listaFazeNomenclator = listaFazeNomenclator.filterIndexed { index, _ -> index != fIdx } },
                                        label = { Text("⚙️ ${fIdx + 1}. $fItem ✕", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
// ZONĂ NOUĂ: Consumabile & Auxiliare asociate (Aracet, Lac, Feronerie, etc.)
                        Text("Consumabile & Materiale Auxiliare (Aracet, Lac, Feronerie):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = consumabilNumeText,
                                onValueChange = { consumabilNumeText = it },
                                label = { Text("Nume consumabil (ex: Aracet)") },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = consumabilNormaText,
                                onValueChange = { consumabilNormaText = it },
                                label = { Text("Consum/buc") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.9f),
                                singleLine = true
                            )
                            // UM scurt
                            Box(modifier = Modifier.weight(0.7f)) {
                                OutlinedTextField(
                                    value = consumabilUnitateText,
                                    onValueChange = { consumabilUnitateText = it },
                                    label = { Text("UM") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                            Button(
                                onClick = {
                                    val cNume = consumabilNumeText.trim()
                                    val cNorma = consumabilNormaText.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    val cUM = consumabilUnitateText.trim().ifEmpty { "buc" }
                                    if (cNume.isNotEmpty() && cNorma > 0.0) {
                                        val nouaLista = listaConsumabileNomenclator.toMutableList()
                                        // Eliminăm dacă exista deja cu același nume
                                        nouaLista.removeAll { it["numeConsumabil"]?.toString()?.equals(cNume, ignoreCase = true) == true }
                                        nouaLista.add(
                                            mapOf(
                                                "numeConsumabil" to cNume,
                                                "normaPerBuc" to cNorma,
                                                "unitate" to cUM
                                            )
                                        )
                                        listaConsumabileNomenclator = nouaLista
                                        consumabilNumeText = ""
                                        consumabilNormaText = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))
                            ) {
                                Text("+ AUX", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (listaConsumabileNomenclator.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Consumabile asociate (apasă pentru ștergere):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                listaConsumabileNomenclator.forEachIndexed { cIdx, cItem ->
                                    val cNume = cItem["numeConsumabil"]?.toString() ?: ""
                                    val cNorma = formateazaNumarFaraNotatieStiintifica(cItem["normaPerBuc"])
                                    val cUM = cItem["unitate"]?.toString() ?: ""
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            listaConsumabileNomenclator = listaConsumabileNomenclator.filterIndexed { index, _ -> index != cIdx }
                                        },
                                        label = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("🧪 $cNume", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text("$cNorma $cUM / buc  ✕", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC3545))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Atelier asociat (Atelier 0 este opțional pentru prelucrare):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            optiuniAteliere.forEach { at ->
                                val textEticheta = if (at == "Atelier0") "Atelier 0 (Prelucrare)" else at
                                FilterChip(selected = atelierSelectatAcum == at, onClick = { atelierSelectatAcum = at }, label = { Text(textEticheta, fontSize = 10.sp) })
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (esteModEditarePiesa) {
                                OutlinedButton(onClick = {
                                    esteModEditarePiesa = false; IDReperText = ""; denumirePiesaText = ""; cantitateNecesaraText = ""; normaConsumText = ""
                                    subansambluDestinatieText = ""; cantitateSubansambluText = "1"; hartaSubansambleNomenclator = emptyMap()
                                    fazaPrelucrareText = ""; listaFazeNomenclator = emptyList()
                                    consumabilNumeText = ""; consumabilNormaText = ""; listaConsumabileNomenclator = emptyList()
                                }, modifier = Modifier.weight(1f)) { Text("ANULEAZĂ") }
                            }
                            Button(
                                onClick = {
                                    val qtyS = cantitateNecesaraText.toIntOrNull() ?: 0
                                    if (IDReperText.trim().isEmpty() || denumirePiesaText.trim().isEmpty() || qtyS <= 0) { statusMesajText = "Completați corect!"; return@Button }
                                    val docId = modelActiv["idDoc"]?.toString() ?: ""
                                    if (docId.isEmpty()) { statusMesajText = "Eroare: ID Document inexistent!"; return@Button }
                                    seIncarca = true
// Sincronizare automată în caz că utilizatorul a uitat să apese butonul "+" la subansamble
                                    var hartaFinalaSubansamble = hartaSubansambleNomenclator.toMutableMap()
                                    val subansambluRamasInText = subansambluDestinatieText.trim().lowercase()
                                    if (subansambluRamasInText.isNotEmpty() && !hartaFinalaSubansamble.containsKey(subansambluRamasInText)) {
                                        val qRamas = cantitateSubansambluText.toIntOrNull() ?: 1
                                        hartaFinalaSubansamble[subansambluRamasInText] = qRamas
                                    }
                                    val subansambleFinaleList = hartaFinalaSubansamble.keys.toList()
                                    val stringSubansambleComplet = subansambleFinaleList.joinToString(", ")
// Sincronizare automată în caz că a uitat să apese "+" la faze
                                    var fazeListFinale = listaFazeNomenclator
                                    val fazaRamasaInText = fazaPrelucrareText.trim()
                                    if (fazaRamasaInText.isNotEmpty() && !fazeListFinale.contains(fazaRamasaInText)) {
                                        fazeListFinale = fazeListFinale + fazaRamasaInText
                                    }
                                    val stringFazeComplet = fazeListFinale.joinToString(", ")
                                    val retetaCurentaBOM = (modelActiv["retetaBOM"] as? List<*>) ?: listOf<Any>()
                                    val mutabilRetetaBOM = retetaCurentaBOM.map { it as? Map<String, Any> ?: emptyMap() }.toMutableList()
                                    val normaConsumVal = normaConsumText.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    val structuraPiesaMap = mapOf(
                                        "reperId" to IDReperText.trim(),
                                        "denumirePiesa" to denumirePiesaText.trim(),
                                        "cantitateNecesara" to qtyS,
                                        "normaConsum" to normaConsumVal,
                                        "atelierAsociat" to atelierSelectatAcum,
                                        "subansambluDestinatie" to stringSubansambleComplet,
                                        "subansambleList" to subansambleFinaleList,
                                        "cantitatiSubansamble" to hartaFinalaSubansamble,
                                        "fazaPrelucrare" to stringFazeComplet,
                                        "fazeList" to fazeListFinale,
                                        "consumabileList" to listaConsumabileNomenclator
                                    )
                                    if (esteModEditarePiesa && indexPiesaEditata >= 0) { mutabilRetetaBOM[indexPiesaEditata] = structuraPiesaMap } else { mutabilRetetaBOM.add(structuraPiesaMap) }
                                    firestore.collection("comenzi_productie").document(docId).set(mapOf("retetaBOM" to mutabilRetetaBOM), SetOptions.merge())
                                        .addOnSuccessListener {
                                            seIncarca = false; esteModEditarePiesa = false; IDReperText = ""; denumirePiesaText = ""; cantitateNecesaraText = ""; normaConsumText = ""
                                            subansambluDestinatieText = ""; cantitateSubansambluText = "1"; hartaSubansambleNomenclator = emptyMap()
                                            fazaPrelucrareText = ""; listaFazeNomenclator = emptyList()
                                            consumabilNumeText = ""; consumabilNormaText = ""; listaConsumabileNomenclator = emptyList()
                                            statusMesajText = "Rețetă salvată în Cloud!"
                                            val modelActualizat = modelActiv.toMutableMap()
                                            modelActualizat["retetaBOM"] = mutabilRetetaBOM
                                            modelSelectatPtReteta = modelActualizat
                                        }
                                        .addOnFailureListener { seIncarca = false; statusMesajText = "Eroare salvat în cloud." }
                                },
                                modifier = Modifier.weight(1.5f), enabled = !seIncarca, colors = ButtonDefaults.buttonColors(containerColor = if (esteModEditarePiesa) Color(0xFF0D6EFD) else MaterialTheme.colorScheme.primary)
                            ) { Text(if (esteModEditarePiesa) "MODIFICĂ REPER 📝" else "ADAUGĂ REPER IN RETETĂ 💾", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
// --- 5. CASERTA TEXT STATUS FIXATĂ ---
        item {
            if (statusMesajText.isNotEmpty()) {
                Text(
                    text = statusMesajText,
                    color = if (statusMesajText.contains("Eroare")) Color(0xFFDC3545) else Color(0xFF198754),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
// --- 6. LISTARE ELEMENTE REPERE CU BUTOANE ACTIVE ---
        // --- 6. LISTARE ELEMENTE REPERE CU BUTOANE ACTIVE ---
        val pieseAfisateLista = if (modelSelectatPtReteta != null) {
            (modelSelectatPtReteta?.get("retetaBOM") as? List<*>) ?: listOf<Any>() // <- Adăugat <Any>
        } else {
            listOf<Any>() // <- Adăugat <Any>
        }

        itemsIndexed(items = pieseAfisateLista) { idx, anyNode ->
            val piesa = anyNode as? Map<*, *> ?: mapOf<Any, Any>()
            val rId = piesa["reperId"]?.toString() ?: "unknown"
            val dNume = piesa["denumirePiesa"]?.toString() ?: rId
            val fAtelier = piesa["atelierAsociat"]?.toString() ?: "Atelier1"
            val fDestinatie = piesa["subansambluDestinatie"]?.toString() ?: ""
            val fFaza = piesa["fazaPrelucrare"]?.toString() ?: ""
            val cQty = (piesa["cantitateNecesara"] as? Long)?.toInt() ?: 1
            val fNormaConsum = (piesa["normaConsum"] as? Number)?.toDouble() ?: 0.0
            val fConsumabileAfisare = (piesa["consumabileList"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

            Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(10.dp)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = dNume, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        val textSubInfo = if (fDestinatie.isNotEmpty()) " | Destinații: $fDestinatie" else ""
                        val textFazaInfo = if (fFaza.isNotEmpty()) " | Faze: $fFaza" else ""
                        val textNormaInfo = if (fNormaConsum > 0) " | Normă: ${formateazaNumarFaraNotatieStiintifica(fNormaConsum)} mp/săpt" else ""
                        Text(text = "Cod: $rId | Atelier: $fAtelier$textFazaInfo$textSubInfo$textNormaInfo | Necesar: $cQty buc", fontSize = 12.sp, color = Color.Gray)

                        if (fConsumabileAfisare.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val textAux = fConsumabileAfisare.joinToString(", ") { c ->
                                "${c["numeConsumabil"]} (${formateazaNumarFaraNotatieStiintifica(c["normaPerBuc"])} ${c["unitate"]}/buc)"
                            }
                            Text(text = "🧪 Auxiliare: $textAux", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF00695C))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
// BUTON EDITARE REPER
                        OutlinedButton(
                            onClick = {
                                esteModEditarePiesa = true; indexPiesaEditata = idx; IDReperText = rId; denumirePiesaText = dNume
                                cantitateNecesaraText = cQty.toString(); atelierSelectatAcum = fAtelier; statusMesajText = ""
                                val fNormaConsumStr = formateazaNumarFaraNotatieStiintifica(piesa["normaConsum"])
                                normaConsumText = fNormaConsumStr
// Preluare dinamică subansamble din cloud sau din string-ul vechi salvat anterior
                                val fCantitatiMap = (piesa["cantitatiSubansamble"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                                    val key = k?.toString()?.trim()?.lowercase() ?: return@mapNotNull null
                                    val cant = (v as? Long)?.toInt() ?: (v as? Int) ?: 1
                                    key to cant
                                }?.toMap() ?: emptyMap()

                                val fSubansambleList = (piesa["subansambleList"] as? List<*>)?.mapNotNull { it?.toString()?.trim()?.lowercase() } ?: emptyList()
                                val listaSubTmp = if (fSubansambleList.isNotEmpty()) fSubansambleList else if (fDestinatie.isNotEmpty()) fDestinatie.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() } else emptyList()

                                hartaSubansambleNomenclator = if (fCantitatiMap.isNotEmpty()) {
                                    fCantitatiMap
                                } else {
                                    // Dacă nu are cantități explicite salvate anterior, presupunem 1 buc per subansamblu sau împărțim
                                    listaSubTmp.associateWith { 1 }
                                }
                                subansambluDestinatieText = ""
                                cantitateSubansambluText = "1"
// Preluare dinamică faze din cloud sau text vechi
                                val fFazeList = (piesa["fazeList"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                listaFazeNomenclator = if (fFazeList.isNotEmpty()) fFazeList else if (fFaza.isNotEmpty()) fFaza.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
                                fazaPrelucrareText = ""

// Preluare dinamică consumabile asociate
                                val fConsumabileRaw = (piesa["consumabileList"] as? List<*>) ?: emptyList<Any>()
                                listaConsumabileNomenclator = fConsumabileRaw.mapNotNull { it as? Map<String, Any> }
                                consumabilNumeText = ""
                                consumabilNormaText = ""
                            },
                            shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Editare ✏️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD)) }
// BUTON ȘTERGERE REPER
                        Button(
                            onClick = {
                                seIncarca = true
                                val mutabilStergere = pieseAfisateLista.map { it as? Map<String, Any> ?: emptyMap() }.toMutableList()
                                mutabilStergere.removeAt(idx)
                                firestore.collection("comenzi_productie").document(modelSelectatPtReteta?.get("idDoc")?.toString() ?: "").set(mapOf("retetaBOM" to mutabilStergere), SetOptions.merge())
                                    .addOnSuccessListener {
                                        seIncarca = false; statusMesajText = "Reper eliminat din rețetă!"
                                        val modelActualizat = modelSelectatPtReteta?.toMutableMap()
                                        modelActualizat?.set("retetaBOM", mutabilStergere)
                                        modelSelectatPtReteta = modelActualizat
// Închide modul de editare dacă piesa curentă tocmai a fost ștearsă
                                        if (esteModEditarePiesa && indexPiesaEditata == idx) {
                                            esteModEditarePiesa = false
                                            IDReperText = ""; denumirePiesaText = ""; cantitateNecesaraText = ""
                                            subansambluDestinatieText = ""; cantitateSubansambluText = "1"; hartaSubansambleNomenclator = emptyMap()
                                            fazaPrelucrareText = ""; listaFazeNomenclator = emptyList()
                                            consumabilNumeText = ""; consumabilNormaText = ""; listaConsumabileNomenclator = emptyList()
                                        }
                                    }
                                    .addOnFailureListener { seIncarca = false; statusMesajText = "Eroare rețea la eliminare." }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), enabled = !seIncarca
                        ) { Text("Șterge 🗑️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    }
                }
            }
        }
    }
}