package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.ThumbUp
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

data class StocMonitorStatus(
    val idReper: String,
    val denumire: String,
    val stocFizicCurent: Int,
    val tintaTotalaPlanuri: Int,
    val procentAcoperire: Float,
    val idDocumentStoc: String,
    val scaunNume: String,
    val atelierResponsabil: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GestiuneStocuriScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    var listaComenziActive by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaStocuriFaze by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var idStocSelectatPtEditare by remember { mutableStateOf("") }
    var valoareNouaStocText by remember { mutableStateOf("") }
    var atelierSelectat by remember { mutableStateOf<String?>(null) }

    var mesajStatus by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) listaComenziActive = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
        }
        firestore.collection("stocuri_faze").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) listaStocuriFaze = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
        }
    }

    // --- PROCESARE MAPARE STOCURI CU CUMULARE TRANS-SĂPTĂMÂNALĂ AUTO-CORECTATĂ ---
    val stocuriGrupatePeAteliere = remember(listaComenziActive, listaStocuriFaze) {
        val listaRezultat = mutableListOf<StocMonitorStatus>()

        listaComenziActive.forEach { comanda ->
            val starePlan = comanda["stare"]?.toString() ?: "In Lucru"
            if (starePlan == "In Lucru") {
                val cantitateScauneFinale = (comanda["cantitateScaune"] as? Long)?.toInt() ?: 0
                val modelNumeScaun = comanda["modelNume"]?.toString() ?: "Scaun Active"
                val retetaBOM = (comanda["retetaBOM"] as? List<*>)?.mapNotNull { it } ?: listOf<Any>()

                // 1. Extragere piese plate din rețetă
                retetaBOM.forEach { nod ->
                    val comp = nod as? Map<*, *> ?: emptyMap<Any, Any>()
                    val reperId = comp["reperId"]?.toString() ?: ""
                    val denumirePiesa = comp["denumirePiesa"]?.toString() ?: reperId.uppercase()
                    val atelierPiesa = comp["atelierAsociat"]?.toString() ?: "Atelier1"
                    val cantitateNecesaraPerScaun = (comp["cantitateNecesara"] as? Long)?.toInt() ?: 1

                    val tintaTotalaPiesa = cantitateScauneFinale * cantitateNecesaraPerScaun
                    val stocIdDoc = "${reperId}_$atelierPiesa"

                    // Sumare inteligentă trans-săptămânală a stocurilor înregistrate anterior
                    val stocCurent = listaStocuriFaze
                        .filter {
                            val idDocStr = it["idDoc"]?.toString() ?: ""
                            idDocStr == stocIdDoc ||
                                    idDocStr.startsWith("${stocIdDoc}_") ||
                                    it["reperId"]?.toString() == reperId
                        }
                        .sumOf { (it["stocDisponibil"] as? Long)?.toInt() ?: 0 }

                    val procent = if (tintaTotalaPiesa > 0) ((stocCurent.toFloat() / tintaTotalaPiesa) * 100).coerceIn(0f, 100f) else 0f

                    listaRezultat.add(StocMonitorStatus(reperId, denumirePiesa, stocCurent, tintaTotalaPiesa, procent, stocIdDoc, modelNumeScaun, atelierPiesa))
                }

                // 2. Extragere stoc produs final (Atelierul 4)
                val modelIdScaun = comanda["modelId"]?.toString() ?: ""
                val atelierScaunFinal = comanda["atelierAsociat"]?.toString() ?: "Atelier4"
                if (modelIdScaun.isNotEmpty()) {
                    val stocIdDocScaun = "${modelIdScaun}_$atelierScaunFinal"

                    val stocCurentScaune = listaStocuriFaze
                        .filter {
                            val idDocStr = it["idDoc"]?.toString() ?: ""
                            idDocStr == stocIdDocScaun ||
                                    idDocStr.startsWith("${stocIdDocScaun}_") ||
                                    it["reperId"]?.toString() == modelIdScaun
                        }
                        .sumOf { (it["stocDisponibil"] as? Long)?.toInt() ?: 0 }

                    val procent = if (cantitateScauneFinale > 0) ((stocCurentScaune.toFloat() / cantitateScauneFinale) * 100).coerceIn(0f, 100f) else 0f

                    listaRezultat.add(StocMonitorStatus(modelIdScaun, "Scaun Complet Ambalat", stocCurentScaune, cantitateScauneFinale, procent, stocIdDocScaun, modelNumeScaun, atelierScaunFinal))
                }
            }
        }
        listaRezultat.distinctBy { it.idDocumentStoc }.groupBy { it.atelierResponsabil }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- ZONĂ FIXĂ (ANTET & FILTRE OPERATOR) ---
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monitorizare Stocuri", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Situația componentelor din lotul de lucru curent PLIMOB", fontSize = 12.sp, color = Color.Gray)
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

            if (mesajStatus.isNotEmpty()) {
                Text(text = mesajStatus, fontWeight = FontWeight.Bold, color = Color(0xFF198754), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            // --- SELECȚIE INTELIGENTĂ FILTRE PE 2 COLOANE ---
            val ateliereDisponibile = stocuriGrupatePeAteliere.keys.sorted()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FilterChip(
                        selected = atelierSelectat == null,
                        onClick = { atelierSelectat = null },
                        label = { Text("TOATE ATELIERELE", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ateliereDisponibile.forEach { atelierCheie ->
                    val numeScurt = when(atelierCheie) {
                        "Atelier0" -> "ATELIERUL 0"
                        "Atelier1" -> "ATELIERUL 1"
                        "Atelier2" -> "ATELIERUL 2"
                        "Atelier3" -> "ATELIERUL 3"
                        "Atelier4" -> "ATELIERUL 4"
                        else -> atelierCheie.uppercase()
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FilterChip(
                            selected = atelierSelectat == atelierCheie,
                            onClick = { atelierSelectat = if (atelierSelectat == atelierCheie) null else atelierCheie },
                            label = { Text(numeScurt, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if ((ateliereDisponibile.size + 1) % 2 != 0) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

// --- ZONĂ SCROLLABILĂ CORECTATĂ (FĂRĂ FOREACH ILEGAL ÎN LAZYCOLUMN) ---
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (stocuriGrupatePeAteliere.isEmpty()) {
                item { Text("Nu există ordine în lucru. Lansați un plan din Fereastra 2.", color = Color.Gray, fontSize = 13.sp) }
            }
            val ateliereDeAfisat = if (atelierSelectat != null) listOf(atelierSelectat!!) else stocuriGrupatePeAteliere.keys.sorted()
            ateliereDeAfisat.forEach { atelierCheie ->
                item {
                    val numeSectieMapeat = when(atelierCheie) {
                        "Atelier0" -> "⚙️ ATELIERUL 0 (PRELUCRARE OPȚIONALĂ PIESE)"
                        "Atelier1" -> "🪵 DEPOZIT / ATELIERUL 1 (DEBITARE REPERE BRUTE)"
                        "Atelier2" -> "🔨 ATELIERUL 2 (MONTAJ SUBANSAMBLE)"
                        "Atelier3" -> "🎨 ATELIERUL 3 (FINISAJ & VOPSITORIE)"
                        "Atelier4" -> "📦 ATELIERUL 4 (TAPIȚERIE & AMBALAJ FINAL)"
                        else -> "📍 ${atelierCheie.uppercase()}"
                    }
                    Text(
                        text = numeSectieMapeat,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                val pieseDinAtelier = stocuriGrupatePeAteliere[atelierCheie] ?: emptyList()
                items(pieseDinAtelier) { itemStoc ->
                    val esteEditatAcum = idStocSelectatPtEditare == itemStoc.idDocumentStoc
                    val procentInt = itemStoc.procentAcoperire.toInt()
                    val (textStatus, culoareStatus) = when {
                        procentInt >= 100 -> Pair("STOC OK ✅", Color(0xFF198754))
                        procentInt >= 75  -> Pair("APROAPE GATA 🔵", Color(0xFF0D6EFD))
                        procentInt >= 50  -> Pair("ALERTĂ BUFFER 🟡", Color(0xFFFBC02D))
                        procentInt >= 25  -> Pair("INSUFICIENT 🟠", Color(0xFFE65100))
                        else              -> Pair("CRITIC URGENT 🔴", Color(0xFFDC3545))
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = if (esteEditatAcum) 2.dp else 1.dp, color = if (esteEditatAcum) Color(0xFF198754) else Color(0xFFE5E5E5), shape = RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Column(modifier = Modifier.fillMaxWidth().padding(end = 95.dp)) {
                                    Text(text = itemStoc.scaunNume.uppercase(), fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFFE65100))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = itemStoc.denumire, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF212529))
                                }
// Monitorizare procentaj cu auto-scale și Like albastru
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                                ) {
                                    var dimensiuneFontProcent by remember(itemStoc.procentAcoperire) { mutableStateOf(24.sp) }
                                    Text(
                                        text = "$procentInt%",
                                        fontSize = dimensiuneFontProcent,
                                        fontWeight = FontWeight.Black,
                                        color = culoareStatus,
                                        maxLines = 1,
                                        softWrap = false,
                                        onTextLayout = { textLayoutResult ->
                                            if (textLayoutResult.didOverflowWidth || textLayoutResult.didOverflowHeight) {
                                                val dimensiuneNoua = (dimensiuneFontProcent.value - 2f)
                                                if (dimensiuneNoua >= 14f) dimensiuneFontProcent = dimensiuneNoua.sp
                                            }
                                        }
                                    )
                                    AnimatedVisibility(visible = procentInt >= 100) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Icon(
                                                imageVector = Icons.Outlined.ThumbUp,
                                                contentDescription = "Stoc Asigurat",
                                                tint = Color(0xFF1877F2),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val factorLatime = (itemStoc.procentAcoperire / 100f).coerceIn(0.0f, 1.0f)
                            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFE9ECEF), RoundedCornerShape(3.dp))) {
                                if (factorLatime > 0f) Box(modifier = Modifier.fillMaxWidth(factorLatime).fillMaxHeight().background(color = culoareStatus, shape = RoundedCornerShape(3.dp)))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA), RoundedCornerShape(6.dp)).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("ȚINTĂ PLAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text("${itemStoc.tintaTotalaPlanuri} buc", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF495057))
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("STOC FIZIC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text(text = "${itemStoc.stocFizicCurent} buc", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (itemStoc.stocFizicCurent < 0) Color(0xFFDC3545) else Color(0xFF198754))
                                }
                                Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("STARE RECO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text(text = textStatus, fontSize = 12.sp, fontWeight = FontWeight.Black, color = culoareStatus)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!esteEditatAcum) {
                                OutlinedButton(
                                    onClick = { idStocSelectatPtEditare = itemStoc.idDocumentStoc; valoareNouaStocText = itemStoc.stocFizicCurent.toString(); mesajStatus = "" },
                                    modifier = Modifier.align(Alignment.End),
                                    shape = RoundedCornerShape(6.dp)
                                ) { Text("Ajustează Stoc ✏️", fontSize = 11.sp) }
                            }
                            AnimatedVisibility(visible = esteEditatAcum) {
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = valoareNouaStocText,
                                        onValueChange = { valoareNouaStocText = it },
                                        label = { Text("Stoc manual găsit la inventar") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    // --- ÎNLOCUIEȘTE DOAR LOGICA ONCLICK A BUTONULUI "SALVEAZĂ" PENTRU CORECTARE ABSOLUTĂ ---
                                    Button(
                                        onClick = {
                                            val stocNouInt = valoareNouaStocText.toIntOrNull() ?: 0
                                            seIncarca = true
                                            mesajStatus = "Se unifică stocul pe luna curentă..."

                                            // 1. Calculăm dinamic luna curentă (ex: 2026-M10)
                                            val calendar = java.util.Calendar.getInstance()
                                            val anCurent = calendar.get(java.util.Calendar.YEAR)
                                            val lunaCurenta = calendar.get(java.util.Calendar.MONTH) + 1
                                            val perioadaLunaStr = String.format("%d-M%02d", anCurent, lunaCurenta)

                                            // 2. Citim toate documentele parazite din cloud care dublează acest reper
                                            firestore.collection("stocuri_faze")
                                                .get()
                                                .addOnSuccessListener { snapshot ->
                                                    val batch = firestore.batch()
                                                    val idDocPrincipal = itemStoc.idDocumentStoc

                                                    // Identificăm toate dublurile trans-săptămânale sau vechi ale acelei piese
                                                    val documenteDeCuratat = snapshot.documents.filter { doc ->
                                                        val idDocStr = doc.id
                                                        idDocStr == idDocPrincipal ||
                                                                idDocStr.startsWith("${idDocPrincipal}_") ||
                                                                doc.getString("reperId") == itemStoc.idReper
                                                    }

                                                    // 3. Aplicăm curățarea lunară: resetăm documentele vechi și salvăm noua valoare pe cel permanent
                                                    documenteDeCuratat.forEach { doc ->
                                                        if (doc.id == idDocPrincipal) {
                                                            batch.set(doc.reference, mapOf(
                                                                "reperId" to itemStoc.idReper,
                                                                "faza" to "Faza ${itemStoc.atelierResponsabil.replace("Atelier", "")} - Monitorizare",
                                                                "stocDisponibil" to stocNouInt, // Va scrie exact valoarea dorită (chiar și 0)
                                                                "perioada" to perioadaLunaStr   // Schimbat în formatul lunar permanent
                                                            ), com.google.firebase.firestore.SetOptions.merge())
                                                        } else {
                                                            // Forțăm 0 pe toate documentele istorice/parazite pentru ca .sumOf să nu mai adune resturi din trecut
                                                            batch.update(doc.reference, "stocDisponibil", 0)
                                                        }
                                                    }

                                                    // 4. Executăm tranzacția în Firebase
                                                    batch.commit()
                                                        .addOnSuccessListener {
                                                            seIncarca = false
                                                            idStocSelectatPtEditare = ""
                                                            valoareNouaStocText = ""
                                                            mesajStatus = "Inventar salvat definitiv pe stocul lunar! ✅"
                                                        }
                                                        .addOnFailureListener {
                                                            seIncarca = false
                                                            mesajStatus = "Eroare la scrierea pachetului de date."
                                                        }
                                                }
                                                .addOnFailureListener {
                                                    seIncarca = false
                                                    mesajStatus = "Eroare la citirea structurii de stoc."
                                                }
                                        },
                                        enabled = !seIncarca && valoareNouaStocText.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754))
                                    ) { Text("SALVEAZĂ") }



                                }
                            }
                        }
                    }
                }
            }
        }
    }
}