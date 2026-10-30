package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
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

data class ReperAuditInfo(
    val reperId: String,
    val denumire: String,
    val cantitate: Int,
    val atelier: String,
    val atelierNormalizat: String,
    val subansambluDestinatie: String,
    val subansambleList: List<String>,
    val fazaPrelucrare: String,
    val fazeList: List<String>,
    val consumabileList: List<Map<String, Any>>,
    val areAvertismentAtelierO: Boolean,
    val areAvertismentFaraAtelier: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AuditNomenclatorScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    var listaComenziModele by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var seIncarca by remember { mutableStateOf(true) }
    var modelSelectatId by remember { mutableStateOf<String?>(null) }
    var filtruAtelierSelectat by remember { mutableStateOf("TOATE") }
    var textCautare by remember { mutableStateOf("") }

    // Stare extindere pe fiecare secțiune de atelier
    val ateliereExtinse = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
                listaComenziModele = list
                if (modelSelectatId == null && list.isNotEmpty()) {
                    modelSelectatId = list.first()["modelId"]?.toString() ?: list.first()["idDoc"]?.toString()
                }
                seIncarca = false
            }
        }
    }

    val modelCurent = remember(listaComenziModele, modelSelectatId) {
        listaComenziModele.find { (it["modelId"]?.toString() ?: it["idDoc"]?.toString()) == modelSelectatId }
    }

    val modelNumeCurent = modelCurent?.get("modelNume")?.toString() ?: "Produs"
    val cantitateScaunePlan = (modelCurent?.get("cantitateScaune") as? Long)?.toInt() ?: 0
    val starePlan = modelCurent?.get("stare")?.toString() ?: "Nespecificat"
    val retetaBruta = modelCurent?.get("retetaBOM") as? List<*> ?: emptyList<Any>()

    // Procesare și validare repere rețetă
    val repereProcesate = remember(retetaBruta) {
        retetaBruta.mapNotNull { nod ->
            val map = nod as? Map<*, *> ?: return@mapNotNull null
            val rId = map["reperId"]?.toString()?.trim() ?: "unknown"
            val denumire = map["denumirePiesa"]?.toString()?.trim() ?: rId
            val cantitate = (map["cantitateNecesara"] as? Long)?.toInt() ?: 1
            val rawAtelier = map["atelierAsociat"]?.toString()?.trim() ?: ""

            val areAvertismentAtelierO = rawAtelier.equals("AtelierO", ignoreCase = true)
            val areAvertismentFaraAtelier = rawAtelier.isEmpty()

            val normAtelier = when {
                areAvertismentAtelierO || rawAtelier.equals("Atelier0", ignoreCase = true) -> "Atelier0"
                rawAtelier.equals("Atelier1", ignoreCase = true) -> "Atelier1"
                rawAtelier.equals("Atelier2", ignoreCase = true) -> "Atelier2"
                rawAtelier.equals("Atelier3", ignoreCase = true) -> "Atelier3"
                rawAtelier.equals("Atelier4", ignoreCase = true) -> "Atelier4"
                rawAtelier.isEmpty() -> "Atelier1"
                else -> rawAtelier
            }

            val subansamblu = map["subansambluDestinatie"]?.toString()?.trim() ?: ""
            val subList = (map["subansambleList"] as? List<*>)?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
            val faza = map["fazaPrelucrare"]?.toString()?.trim() ?: ""
            val fList = (map["fazeList"] as? List<*>)?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
            val consumabile = (map["consumabileList"] as? List<*>)?.mapNotNull { it as? Map<String, Any> } ?: emptyList()

            ReperAuditInfo(
                reperId = rId,
                denumire = denumire,
                cantitate = cantitate,
                atelier = rawAtelier.ifEmpty { "Atelier1 (implicit)" },
                atelierNormalizat = normAtelier,
                subansambluDestinatie = subansamblu,
                subansambleList = subList,
                fazaPrelucrare = faza,
                fazeList = fList,
                consumabileList = consumabile,
                areAvertismentAtelierO = areAvertismentAtelierO,
                areAvertismentFaraAtelier = areAvertismentFaraAtelier
            )
        }
    }

    // Filtrare după căutare și atelier
    val repereFiltrate = remember(repereProcesate, textCautare, filtruAtelierSelectat) {
        repereProcesate.filter { r ->
            val matchText = textCautare.isEmpty() ||
                    r.denumire.contains(textCautare, ignoreCase = true) ||
                    r.reperId.contains(textCautare, ignoreCase = true) ||
                    r.consumabileList.any { (it["numeConsumabil"]?.toString() ?: "").contains(textCautare, ignoreCase = true) }
                    r.reperId.contains(textCautare, ignoreCase = true) ||
                    r.subansambluDestinatie.contains(textCautare, ignoreCase = true) ||
                    r.fazaPrelucrare.contains(textCautare, ignoreCase = true)

            val matchAtelier = filtruAtelierSelectat == "TOATE" || r.atelierNormalizat == filtruAtelierSelectat
            matchText && matchAtelier
        }
    }

    // Configurare grupuri ateliere standard
    val ateliereConfig = listOf(
        "Atelier0" to Triple("ATELIERUL 0 - PRELUCRARE BRUTĂ / OPȚIONALĂ ⚙️", Color(0xFFFFF3E0), Color(0xFFE65100)),
        "Atelier1" to Triple("ATELIERUL 1 - DEBITARE BRUTĂ & DIGITAL PRINT 🖨️", Color(0xFFE8F5E9), Color(0xFF2E7D32)),
        "Atelier2" to Triple("ATELIERUL 2 - MONTAJ SUBANSAMBLE 🔨", Color(0xFFE3F2FD), Color(0xFF1565C0)),
        "Atelier3" to Triple("ATELIERUL 3 - FINISAJ & VOPSITORIE 🎨", Color(0xFFF3E5F5), Color(0xFF7B1FA2)),
        "Atelier4" to Triple("ATELIERUL 4 - TAPIȚERIE & AMBALARE FINALĂ 📦", Color(0xFFFFEBEE), Color(0xFFC62828))
    )

    // Număr total de piese pe bucată de scaun
    val totalPiesePerProdus = remember(repereProcesate) {
        repereProcesate.sumOf { it.cantitate }
    }

    // Verificare inconsistențe / avertismente pe tot produsul
    val numarAvertismente = remember(repereProcesate) {
        repereProcesate.count { it.areAvertismentAtelierO || it.areAvertismentFaraAtelier }
    }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.dp, Color.LightGray.copy(alpha = 0.4f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                FilledTonalIconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Înapoi",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- 1. HEADER ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audit Nomenclator & BOM 🔍",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4527A0)
                        )
                        Text(
                            text = "Verificare structură piese, ateliere asociate și fluxuri",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(top = 6.dp))
            }

            // --- 2. SELECTOR PRODUSE (LOTURI) ---
            item {
                Text(
                    text = "SELECTEAZĂ PRODUSUL PENTRU AUDIT:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listaComenziModele.forEach { model ->
                        val mId = model["modelId"]?.toString() ?: model["idDoc"]?.toString() ?: ""
                        val mNume = model["modelNume"]?.toString() ?: "Scaun"
                        val esteSelectat = (modelSelectatId == mId)
                        FilterChip(
                            selected = esteSelectat,
                            onClick = { modelSelectatId = mId },
                            label = {
                                Text(
                                    text = mNume.uppercase(),
                                    fontWeight = if (esteSelectat) FontWeight.Black else FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
            }

            // --- 3. FIȘĂ SUMAR PRODUS SELECTAT ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFB39DDB))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "PRODUS ANALIZAT ACUM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
                                Text(text = modelNumeCurent.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF311B92))
                                Text(text = "Stare: $starePlan | Țintă lansată: $cantitateScaunePlan bucăți", fontSize = 11.sp, color = Color.DarkGray)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${repereProcesate.size} repere",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF4527A0)
                                )
                                Text(
                                    text = "$totalPiesePerProdus piese / produs",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF512DA8)
                                )
                            }
                        }

                        // Banner avertisment dacă sunt erori de configurare
                        if (numarAvertismente > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⚠️", fontSize = 18.sp)
                                    Text(
                                        text = "Atenție: S-au identificat $numarAvertismente repere cu avertismente de atelier (ex: 'AtelierO' cu litera O sau atelier necompletat)!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. BARA DE CĂUTARE ȘI FILTRE ATELIERE ---
            item {
                OutlinedTextField(
                    value = textCautare,
                    onValueChange = { textCautare = it },
                    placeholder = { Text("Caută reper, cod, destinație sau fază...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filtre rapide pe ateliere
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val optiuniFiltru = listOf("TOATE", "Atelier0", "Atelier1", "Atelier2", "Atelier3", "Atelier4")
                    optiuniFiltru.forEach { atCod ->
                        val esteSelectat = (filtruAtelierSelectat == atCod)
                        val eticheta = when(atCod) {
                            "TOATE" -> "Toate (${repereProcesate.size})"
                            "Atelier0" -> "At. 0 (${repereProcesate.count { it.atelierNormalizat == "Atelier0" }})"
                            "Atelier1" -> "At. 1 (${repereProcesate.count { it.atelierNormalizat == "Atelier1" }})"
                            "Atelier2" -> "At. 2 (${repereProcesate.count { it.atelierNormalizat == "Atelier2" }})"
                            "Atelier3" -> "At. 3 (${repereProcesate.count { it.atelierNormalizat == "Atelier3" }})"
                            "Atelier4" -> "At. 4 (${repereProcesate.count { it.atelierNormalizat == "Atelier4" }})"
                            else -> atCod
                        }
                        FilterChip(
                            selected = esteSelectat,
                            onClick = { filtruAtelierSelectat = atCod },
                            label = { Text(eticheta, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }

            // --- 5. SECȚIUNI STRUCTURATE PE ATELIERE ---
            if (seIncarca) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF4527A0))
                    }
                }
            }

            if (!seIncarca && repereFiltrate.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Niciun reper găsit pentru filtrul curent.", fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                    }
                }
            }

            // Parcurgem cele 5 ateliere
            ateliereConfig.forEach { (atCod, atStyle) ->
                val (titluAtelier, bgCuloare, textCuloare) = atStyle
                val repereAcestAtelier = repereFiltrate.filter { it.atelierNormalizat == atCod }

                // Dacă atelierul are piese sau filtrul specific a fost setat pe el
                if (repereAcestAtelier.isNotEmpty() || (filtruAtelierSelectat == atCod)) {
                    val esteSectiuneExtinsa = ateliereExtinse[atCod] ?: true

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, textCuloare.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Header Atelier (Clickabil pentru colapsare/extindere)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bgCuloare, RoundedCornerShape(10.dp))
                                        .clickable { ateliereExtinse[atCod] = !esteSectiuneExtinsa }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = titluAtelier,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = textCuloare
                                        )
                                        Text(
                                            text = "${repereAcestAtelier.size} repere configurate • Total: ${repereAcestAtelier.sumOf { it.cantitate }} piese/produs",
                                            fontSize = 11.sp,
                                            color = Color.DarkGray
                                        )
                                    }

                                    Icon(
                                        imageVector = if (esteSectiuneExtinsa) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = textCuloare
                                    )
                                }

                                // Lista reperelor din atelier
                                AnimatedVisibility(visible = esteSectiuneExtinsa) {
                                    Column(
                                        modifier = Modifier.padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (repereAcestAtelier.isEmpty()) {
                                            Text(
                                                text = "Nu există piese configurate pentru acest atelier în rețetă.",
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        } else {
                                            repereAcestAtelier.forEach { reper ->
                                                CardReperAudit(reper = reper, cantitateScaunePlan = cantitateScaunePlan)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardReperAudit(reper: ReperAuditInfo, cantitateScaunePlan: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (reper.areAvertismentAtelierO || reper.areAvertismentFaraAtelier) 1.5.dp else 1.dp,
                color = if (reper.areAvertismentAtelierO || reper.areAvertismentFaraAtelier) Color(0xFFD32F2F) else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(10.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Linie 1: Denumire + Necesar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = reper.denumire,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212529)
                    )
                    Text(
                        text = "Cod Unic: ${reper.reperId}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${reper.cantitate} buc / produs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    if (cantitateScaunePlan > 0) {
                        Text(
                            text = "Total lot: ${reper.cantitate * cantitateScaunePlan} buc",
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Linie 2: Detalii flux tehnologic (Atelier real, Subansamblu Destinație, Faze)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Atelier: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Box(
                    modifier = Modifier
                        .background(
                            if (reper.areAvertismentAtelierO) Color(0xFFFFCDD2) else Color(0xFFE0E0E0),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = reper.atelier,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (reper.areAvertismentAtelierO) Color(0xFFB71C1C) else Color(0xFF37474F)
                    )
                }

                if (reper.areAvertismentAtelierO) {
                    Text("⚠️ (Salvat cu litera O!)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                }
            }

            // Destinație subansamblu / atelier următor
            if (reper.subansambluDestinatie.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔗 Destinație (Următoarea etapă): ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Text(
                        text = reper.subansambluDestinatie,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0D47A1)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔗 Destinație: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Text(
                        text = if (reper.atelierNormalizat == "Atelier4") "Produs Finit (Ambalare)" else "Direct în flux / Fără restricție subansamblu",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            // Faze de prelucrare
            if (reper.fazaPrelucrare.isNotEmpty() || reper.fazeList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val fazeText = if (reper.fazeList.isNotEmpty()) reper.fazeList.joinToString(" ➔ ") else reper.fazaPrelucrare
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚙️ Faze tehnologice: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Text(text = fazeText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5E35B1))
                }
            }

            // Consumabile & Auxiliare asociate
            if (reper.consumabileList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("🧪 Consumabile / Auxiliare asociate:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                    reper.consumabileList.forEach { cMap ->
                        val cNume = cMap["numeConsumabil"]?.toString() ?: ""
                        val cNorma = formateazaNumarFaraNotatieStiintifica(cMap["normaPerBuc"])
                        val cUM = cMap["unitate"]?.toString() ?: ""
                        Row(modifier = Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("• $cNume: ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF004D40))
                            Text("$cNorma $cUM / buc", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}
