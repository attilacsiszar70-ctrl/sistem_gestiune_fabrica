package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ConsumSaptamanal(
    val an: Int,
    val saptamana: Int,
    val cheieSaptamana: String,      // ex: "2026-S16"
    val etichetaScurta: String,      // ex: "S16"
    val dataInceputSapt: String,     // ex: "13 Apr"
    val dataSfarsitSapt: String,     // ex: "19 Apr"
    val totalConsumCalculat: Double, // SUM(cantitateOk * normaConsum)
    val totalScauneEchivalente: Double, // totalConsumCalculat / factorMpPerScaun (sau totalConsumCalculat * factorMpPerScaun)
    val totalPieseProduse: Int,      // Total bucăți raportate
    val numarRaportari: Int          // Număr înregistrări
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraficNormeConsumScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    var listaModeleScaune by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var istoricProductie by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var seIncarca by remember { mutableStateOf(true) }

    // Săptămâna selectată pentru vizualizare detaliată (click pe bară)
    var saptamanaSelectataCheie by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaModeleScaune = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                istoricProductie = snapshot.documents.map { it.data ?: emptyMap() }
                seIncarca = false
            }
        }
    }

    // 1. Extragere hartă norme de consum din Nomenclator: "reperId|atelier" -> normaConsum
    // Acest lucru asigură că norma se aplică STRICT pe atelierul în care este definită piesa
    fun normalizareAtelier(raw: String?): String {
        val s = (raw ?: "").trim()
        return when {
            s.equals("AtelierO", ignoreCase = true) || s.equals("Atelier0", ignoreCase = true) -> "Atelier0"
            s.equals("Atelier1", ignoreCase = true) -> "Atelier1"
            s.equals("Atelier2", ignoreCase = true) -> "Atelier2"
            s.equals("Atelier3", ignoreCase = true) -> "Atelier3"
            s.equals("Atelier4", ignoreCase = true) -> "Atelier4"
            else -> s.lowercase()
        }
    }

    val hartaNormeConsum = remember(listaModeleScaune) {
        val map = mutableMapOf<String, Double>()
        for (model in listaModeleScaune) {
            val retetaBOM = model["retetaBOM"] as? List<*> ?: emptyList<Any>()
            for (item in retetaBOM) {
                val piesa = item as? Map<*, *> ?: continue
                val reperId = piesa["reperId"]?.toString()?.trim()?.lowercase() ?: ""
                val denumire = piesa["denumirePiesa"]?.toString()?.trim()?.lowercase() ?: ""
                val atelier = normalizareAtelier(piesa["atelierAsociat"]?.toString())

                val ncRaw = piesa["normaConsum"]
                val norma = when (ncRaw) {
                    is Double -> ncRaw
                    is Long -> ncRaw.toDouble()
                    is Int -> ncRaw.toDouble()
                    is String -> ncRaw.replace(",", ".").toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }

                if (norma > 0.0) {
                    // Cheie compusă: reper + atelier specific
                    if (reperId.isNotEmpty()) {
                        map["$reperId|$atelier"] = norma
                    }
                    if (denumire.isNotEmpty() && !map.containsKey("$denumire|$atelier")) {
                        map["$denumire|$atelier"] = norma
                    }
                }
            }
        }
        map
    }

    // Parametru factor MP per scaun desfășurat (implicit 0.75 mp / scaun)
    var factorMpPerScaunText by remember { mutableStateOf("0.75") }
    val factorMpPerScaun = factorMpPerScaunText.replace(",", ".").toDoubleOrNull() ?: 0.75

    // Mod de afișare pe grafic: "SCAUNE" (default) sau "MP"
    var modAfisajGrafic by remember { mutableStateOf("SCAUNE") } // "SCAUNE" sau "MP"

    // 2. Calcul totalizat pe fiecare săptămână
    val listaConsumPeSaptamani: List<ConsumSaptamanal> = remember(istoricProductie, hartaNormeConsum, factorMpPerScaun) {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val sdfZiLuna = SimpleDateFormat("dd MMM", Locale("ro", "RO"))

        // Grupăm pe (An, Săptămână)
        val agregator = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Double, Int>>>()

        for (item in istoricProductie) {
            val articolId = item["articolId"]?.toString()?.trim()?.lowercase() ?: ""
            val denumirePiesa = item["denumirePiesa"]?.toString()?.trim()?.lowercase() ?: ""
            val atelierRaportare = normalizareAtelier(item["atelier"]?.toString())

            // Găsim norma de consum STRICT pentru piesa raportată în acel atelier
            val cheieReperAtelier = "$articolId|$atelierRaportare"
            val cheieDenumireAtelier = "$denumirePiesa|$atelierRaportare"
            val norma = hartaNormeConsum[cheieReperAtelier] ?: hartaNormeConsum[cheieDenumireAtelier] ?: 0.0

            if (norma <= 0.0) continue // Dacă reperul nu are normă de consum definită în ACEST atelier, nu intră în calcul

            val cantitateOk = when (val co = item["cantitateOk"]) {
                is Long -> co.toInt()
                is Int -> co
                is Double -> co.toInt()
                is String -> co.toIntOrNull() ?: 0
                else -> 0
            }
            if (cantitateOk <= 0) continue

            // Extragem timestamp-ul
            val ts: Long = when (val t = item["timestamp"]) {
                is Long -> t
                is Double -> t.toLong()
                is String -> t.toLongOrNull() ?: 0L
                else -> 0L
            }
            if (ts <= 0L) continue

            cal.timeInMillis = ts
            val an = cal.get(Calendar.YEAR)
            val saptamana = cal.get(Calendar.WEEK_OF_YEAR)

            val consumLinie = norma * cantitateOk

            val lista = agregator.getOrPut(Pair(an, saptamana)) { mutableListOf() }
            lista.add(Pair(consumLinie, cantitateOk))
        }

        // Construim lista sortată cronologic
        agregator.entries.map { (cheie, valori) ->
            val (an, sapt) = cheie
            val totalConsum = valori.sumOf { it.first }
            val totalPiese = valori.sumOf { it.second }

            // Determinăm intervalul de date (Luni - Duminică) al acelei săptămâni
            val calInterval = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                minimalDaysInFirstWeek = 4
                clear()
                set(Calendar.YEAR, an)
                set(Calendar.WEEK_OF_YEAR, sapt)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            val startStr = sdfZiLuna.format(calInterval.time)
            calInterval.add(Calendar.DAY_OF_WEEK, 6)
            val endStr = sdfZiLuna.format(calInterval.time)

            // Scaune desfășurate: Dacă 1 scaun complet are X mp (ex: 0.75 mp),
            // numărul de scaune echivalente în acei MP este: Total MP / 0.75 mp (sau Total MP * 0.75 dacă factorul e rată de conversie)
            val scauneEchivalente = if (factorMpPerScaun > 0.0) totalConsum / factorMpPerScaun else 0.0

            ConsumSaptamanal(
                an = an,
                saptamana = sapt,
                cheieSaptamana = "$an-S%02d".format(sapt),
                etichetaScurta = "S%02d".format(sapt),
                dataInceputSapt = startStr,
                dataSfarsitSapt = endStr,
                totalConsumCalculat = totalConsum,
                totalScauneEchivalente = scauneEchivalente,
                totalPieseProduse = totalPiese,
                numarRaportari = valori.size
            )
        }.sortedWith(compareBy({ it.an }, { it.saptamana }))
    }

    // Auto-selectează ultima săptămână dacă nu e nimic selectat
    LaunchedEffect(listaConsumPeSaptamani) {
        if (saptamanaSelectataCheie == null && listaConsumPeSaptamani.isNotEmpty()) {
            saptamanaSelectataCheie = listaConsumPeSaptamani.last().cheieSaptamana
        }
    }

    val saptamanaCurentaSelectata = remember(listaConsumPeSaptamani, saptamanaSelectataCheie) {
        listaConsumPeSaptamani.find { it.cheieSaptamana == saptamanaSelectataCheie }
            ?: listaConsumPeSaptamani.lastOrNull()
    }

    // Statistici globale
    val totalConsumGeneral = remember(listaConsumPeSaptamani) {
        listaConsumPeSaptamani.sumOf { it.totalConsumCalculat }
    }
    val totalScauneGeneral = remember(listaConsumPeSaptamani) {
        listaConsumPeSaptamani.sumOf { it.totalScauneEchivalente }
    }
    val valoareMaximaSaptamanala = remember(listaConsumPeSaptamani, modAfisajGrafic) {
        val maxim = if (modAfisajGrafic == "SCAUNE") {
            listaConsumPeSaptamani.maxOfOrNull { it.totalScauneEchivalente } ?: 1.0
        } else {
            listaConsumPeSaptamani.maxOfOrNull { it.totalConsumCalculat } ?: 1.0
        }
        if (maxim <= 0.0) 1.0 else maxim
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Grafic Norme Consum 📊",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Totalizat pe săptămână (Normă × Cantitate realizată)",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Înapoi",
                            tint = Color(0xFF00695C)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. CARD PRINCIPAL SUMAR ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF80CBC4))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (modAfisajGrafic == "SCAUNE") "TOTAL SCAUNE DESFĂȘURATE (CUMULAT)" else "TOTAL CONSUM GENERAL CUMULAT (MP)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00695C)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val valoareMare = if (modAfisajGrafic == "SCAUNE") {
                                    "%.0f scaune".format(Locale.US, totalScauneGeneral)
                                } else {
                                    "%.2f mp".format(Locale.US, totalConsumGeneral)
                                }
                                Text(
                                    text = valoareMare,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF004D40)
                                )
                                if (modAfisajGrafic == "SCAUNE") {
                                    Text(
                                        text = "Echivalent din %.2f mp totalizați (1 scaun = $factorMpPerScaun mp)".format(Locale.US, totalConsumGeneral),
                                        fontSize = 10.sp,
                                        color = Color(0xFF00796B),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00695C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFB2DFDB))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Selector mod afișare și reglare factor MP / scaun
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = modAfisajGrafic == "SCAUNE",
                                    onClick = { modAfisajGrafic = "SCAUNE" },
                                    label = { Text("🪑 SCAUNE (0.75 mp)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                )
                                FilterChip(
                                    selected = modAfisajGrafic == "MP",
                                    onClick = { modAfisajGrafic = "MP" },
                                    label = { Text("📐 METRI PĂTRAȚI", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                )
                            }

                            // Câmp ajustare factor dacă se dorește altă valoare decât 0.75
                            OutlinedTextField(
                                value = factorMpPerScaunText,
                                onValueChange = { factorMpPerScaunText = it },
                                label = { Text("mp/scaun", fontSize = 9.sp) },
                                modifier = Modifier.width(90.dp).height(50.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // --- 2. GRAFICUL TOTALIZAT PE SĂPTĂMÂNI (BAR CHART PERSONALIZAT) ---
            if (listaConsumPeSaptamani.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (modAfisajGrafic == "SCAUNE") "📈 SCAUNE DESFĂȘURATE / SĂPTĂMÂNĂ" else "📈 EVOLUȚIE CONSUM (MP) / SĂPTĂMÂNĂ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF263238)
                                )
                                Text(
                                    text = "Apasă pe o bară pentru detalii",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Zona graficului orizontal derulabil pentru a acomoda toate săptămânile
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                items(listaConsumPeSaptamani) { sapt ->
                                    val esteSelectata = (sapt.cheieSaptamana == saptamanaSelectataCheie)
                                    val valoareAfisata = if (modAfisajGrafic == "SCAUNE") sapt.totalScauneEchivalente else sapt.totalConsumCalculat

                                    val procentInaltime = if (valoareMaximaSaptamanala > 0) {
                                        (valoareAfisata / valoareMaximaSaptamanala).toFloat().coerceIn(0.06f, 1f)
                                    } else 0.06f

                                    val inaltimeAnimata by animateFloatAsState(
                                        targetValue = procentInaltime,
                                        animationSpec = tween(durationMillis = 600),
                                        label = "inaltimeBara"
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(62.dp)
                                            .clickable {
                                                saptamanaSelectataCheie = sapt.cheieSaptamana
                                            }
                                    ) {
                                        // Valoarea unică a săptămânii afișată deasupra barei
                                        val textBara = if (modAfisajGrafic == "SCAUNE") {
                                            "%.0f buc".format(Locale.US, sapt.totalScauneEchivalente)
                                        } else {
                                            "%.1f mp".format(Locale.US, sapt.totalConsumCalculat)
                                        }

                                        Text(
                                            text = textBara,
                                            fontSize = 10.sp,
                                            fontWeight = if (esteSelectata) FontWeight.Black else FontWeight.Bold,
                                            color = if (esteSelectata) Color(0xFF00695C) else Color.DarkGray,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Bara verticală proporțională
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight(inaltimeAnimata * 0.78f)
                                                .width(if (esteSelectata) 38.dp else 30.dp)
                                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                                .background(
                                                    if (esteSelectata) {
                                                        Brush.verticalGradient(
                                                            listOf(Color(0xFF00897B), Color(0xFF004D40))
                                                        )
                                                    } else {
                                                        Brush.verticalGradient(
                                                            listOf(Color(0xFF80CBC4), Color(0xFF00796B))
                                                        )
                                                    }
                                                )
                                                .border(
                                                    width = if (esteSelectata) 2.dp else 0.dp,
                                                    color = if (esteSelectata) Color(0xFF004D40) else Color.Transparent,
                                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                                )
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Eticheta săptămânii (ex: S16)
                                        Text(
                                            text = sapt.etichetaScurta,
                                            fontSize = 11.sp,
                                            fontWeight = if (esteSelectata) FontWeight.Black else FontWeight.Bold,
                                            color = if (esteSelectata) Color(0xFF004D40) else Color.Gray
                                        )
                                        Text(
                                            text = sapt.an.toString(),
                                            fontSize = 8.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. FIȘĂ DETALIATĂ SĂPTĂMÂNĂ SELECTATĂ (O SINGURĂ VALOARE TOTALIZATĂ) ---
            if (saptamanaCurentaSelectata != null) {
                item {
                    val sapt = saptamanaCurentaSelectata
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00695C))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = Color(0xFF00695C),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "SĂPTĂMÂNA ${sapt.saptamana} (${sapt.an})",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF004D40)
                                        )
                                        Text(
                                            text = "Perioada: ${sapt.dataInceputSapt} – ${sapt.dataSfarsitSapt}",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE0F2F1), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = sapt.cheieSaptamana,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF00695C)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Caseta mare cu valoarea totalizată a săptămânii
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "SCAUNE DESFĂȘURATE SĂPTĂMÂNĂ:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "Consum brut: %.2f mp (la 1 scaun = $factorMpPerScaun mp)".format(Locale.US, sapt.totalConsumCalculat),
                                            fontSize = 10.sp,
                                            color = Color.DarkGray
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "%.0f".format(Locale.US, sapt.totalScauneEchivalente),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1B5E20)
                                        )
                                        Text(
                                            text = "scaune echivalente",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("PIESE REALIZATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("${sapt.totalPieseProduse} buc", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF263238))
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("RAPORTĂRI ÎNREGISTRATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("${sapt.numarRaportari} tranșe", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF263238))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. LISTA RECAPITULATIVĂ CU TOATE SĂPTĂMÂNILE (O LOR-LINIE PER SĂPTĂMÂNĂ) ---
            if (listaConsumPeSaptamani.size > 1) {
                item {
                    Text(
                        text = "📋 TOATE SĂPTĂMÂNILE TOTALIZATE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray
                    )
                }

                items(listaConsumPeSaptamani.reversed()) { sapt ->
                    val esteSelectata = (sapt.cheieSaptamana == saptamanaSelectataCheie)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                saptamanaSelectataCheie = sapt.cheieSaptamana
                            }
                            .border(
                                width = if (esteSelectata) 1.5.dp else 1.dp,
                                color = if (esteSelectata) Color(0xFF00695C) else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (esteSelectata) Color(0xFFF2F9F8) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Săptămâna ${sapt.saptamana}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF263238)
                                    )
                                    Text(
                                        text = "(${sapt.an})",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    text = "${sapt.dataInceputSapt} – ${sapt.dataSfarsitSapt} • ${sapt.totalPieseProduse} piese",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val valoarePrincipala = if (modAfisajGrafic == "SCAUNE") {
                                    "%.0f scaune".format(Locale.US, sapt.totalScauneEchivalente)
                                } else {
                                    "%.2f mp".format(Locale.US, sapt.totalConsumCalculat)
                                }
                                Text(
                                    text = valoarePrincipala,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = if (modAfisajGrafic == "SCAUNE") "%.2f mp consum".format(Locale.US, sapt.totalConsumCalculat) else "total săptămână",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // --- 5. STARE GOL / FĂRĂ DATE ---
            if (!seIncarca && listaConsumPeSaptamani.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nu există încă date de consum calculate",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Pentru a vedea valorile în grafic:\n1. Adăugați 'Normă consum' în Nomenclator (BOM) la reperele dorite.\n2. Raportați producție în secții pentru acele repere.",
                                fontSize = 11.sp,
                                color = Color(0xFF5D4037),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (seIncarca) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00695C))
                    }
                }
            }
        }
    }
}
