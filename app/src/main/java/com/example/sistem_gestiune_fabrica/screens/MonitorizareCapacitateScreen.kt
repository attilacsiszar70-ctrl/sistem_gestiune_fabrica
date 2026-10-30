package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import kotlin.math.min
import kotlin.collections.List

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorizareCapacitateScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    var comenziActive by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaStocuriFaze by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var seIncarca by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                comenziActive = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("stocuri_faze").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaStocuriFaze = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
                seIncarca = false
            }
        }
    }

    val loturiActiveInLucru = remember(comenziActive) {
        comenziActive.filter { it["stare"]?.toString() == "In Lucru" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- 1. BARĂ TITLU ȘI NAVIGARE PREMIUM ---
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Analiză Predictivă", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color(0xFF4A148C))
                        Text(text = "Sistem live de calcul capacitate curentă", fontSize = 13.sp, color = Color.Gray)
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
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (seIncarca) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF673AB7), strokeWidth = 5.dp)
                }
            }
        } else if (loturiActiveInLucru.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Text(text = "⚠️ Nu există loturi active 'In Lucru' lansate în fabrică.", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                }
            }
        }

        // --- STRUCTURĂ LOTURI ACTIVE ---
        items(loturiActiveInLucru) { lot ->
            val modelId = lot["modelId"]?.toString() ?: ""
            val modelNume = lot["modelNume"]?.toString() ?: "Scaun"
            val retetaBOM = lot["retetaBOM"] as? List<*> ?: listOf<Any>()

            // Separarea inteligentă a reperelor din rețetă pe cele două secții principale
            val subansambleAtelier2 = retetaBOM.map { (it as? Map<String, Any>) ?: emptyMap() }.filter { it["atelierAsociat"]?.toString() == "Atelier2" }

            val componenteAtelier4 = retetaBOM.map { (it as? Map<String, Any>) ?: emptyMap() }.filter {
                it["atelierAsociat"]?.toString() == "Atelier4" ||
                        it["atelierAsociat"]?.toString() == "Atelier2" ||
                        ((it["atelierAsociat"]?.toString() == "Atelier1" || it["atelierAsociat"]?.toString() == "Atelier0") && (it["subansambluDestinatie"]?.toString() ?: "").isEmpty() && ((it["subansambleList"] as? List<*>)?.isEmpty() ?: true))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header Lot Unificat
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF3E5F5), RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Text(text = "PRODUS: ${modelNume.uppercase()}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF4A148C), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ================= PANEL 1: ATELIERUL 2 (MONTAJ AGREGATE) =================
                    Text(text = "🛠️ SECTIA 2 • CAPACITATE MAXIMĂ ASAMBLARE AGREGATE:", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF37474F))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (subansambleAtelier2.isEmpty()) {
                        Text(text = "   • Nu sunt definite subansamble în Nomenclatorul BOM pentru acest scaun.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
                    } else {
                        val limiteAgregateDedicat = mutableMapOf<String, Int>()
                        val detaliiPiese = mutableMapOf<String, List<String>>()
                        val consumPartajatPerSubansamblu = mutableMapOf<String, Map<String, Int>>()
                        val stocPieseComunePeRaft = mutableMapOf<String, Int>()

                        for (subansamblu in subansambleAtelier2) {
                            val idAgregat = subansamblu["reperId"]?.toString() ?: ""

                            // Mapare piese din rețetă asociate acestui subansamblu (Fie prin text simplu, fie în listă)
                            val pieseAsociate = retetaBOM.map { (it as? Map<String, Any>) ?: emptyMap() }
                                .filter { piesa ->
                                    val atelierOk = piesa["atelierAsociat"]?.toString() == "Atelier1" || piesa["atelierAsociat"]?.toString() == "Atelier0"
                                    val destinatieSimpla = piesa["subansambluDestinatie"]?.toString() ?: ""
                                    val listaDestinatiiDinCloud = (piesa["subansambleList"] as? List<*>)?.map { it.toString().trim().lowercase() } ?: emptyList()
                                    val listaDestinatiiDinString = destinatieSimpla.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

                                    atelierOk && (listaDestinatiiDinCloud.contains(idAgregat.lowercase()) || listaDestinatiiDinString.contains(idAgregat.lowercase()))
                                }

                            var limitaDinPieseDedicate = if (pieseAsociate.isEmpty()) 0 else Int.MAX_VALUE
                            val detaliiTemporare = mutableListOf<String>()
                            val hartaConsumPieseMecanice = mutableMapOf<String, Int>()

                            for (piesaBruta in pieseAsociate) {
                                val codBrut = piesaBruta["reperId"]?.toString() ?: ""
                                val numeBrut = piesaBruta["denumirePiesa"]?.toString() ?: codBrut
                                val necesar = (piesaBruta["cantitateNecesara"] as? Long)?.toInt() ?: 1

                                val idDocStoc = "${codBrut}_Atelier1"
                                val stocFirebase = listaStocuriFaze.find { it["idDoc"]?.toString() == idDocStoc }
                                val cantRaft = (stocFirebase?.get("stocDisponibil") as? Long)?.toInt() ?: 0

                                // O piesă este considerată comună/partajată dacă se duce către mai multe subansamble
                                val destinatieSimplaPiesa = piesaBruta["subansambluDestinatie"]?.toString() ?: ""
                                val listaDestinatiiPiesa = (piesaBruta["subansambleList"] as? List<*>)?.map { it.toString().trim() } ?: emptyList()
                                val estePiesaPartajataGlobal = listaDestinatiiPiesa.size > 1 || destinatieSimplaPiesa.contains(",")
                                if (estePiesaPartajataGlobal) {
                                    stocPieseComunePeRaft[codBrut] = cantRaft
                                    hartaConsumPieseMecanice[codBrut] = necesar
                                } else {
                                    val maxPosibilDedicat = cantRaft / necesar
                                    limitaDinPieseDedicate = min(limitaDinPieseDedicate, maxPosibilDedicat)
                                    detaliiTemporare.add("      ▪ $numeBrut: $cantRaft buc pe raft ➔ Suficient pentru $maxPosibilDedicat unități")
                                }
                            }
                            limiteAgregateDedicat[idAgregat] = if (limitaDinPieseDedicate == Int.MAX_VALUE) 999999 else limitaDinPieseDedicate
                            detaliiPiese[idAgregat] = detaliiTemporare
                            consumPartajatPerSubansamblu[idAgregat] = hartaConsumPieseMecanice
                        }
// --- ALOCARE DINAMICĂ LIVE (ECHILIBRARE PE STOCURI COMUNE) ---
                        val capacitatiAgregateFinale = mutableMapOf<String, Int>()
                        for (sub in subansambleAtelier2) {
                            capacitatiAgregateFinale[sub["reperId"]?.toString() ?: ""] = 0
                        }
                        var saEfectuatOAsamblareNoua = true
                        while (saEfectuatOAsamblareNoua) {
                            saEfectuatOAsamblareNoua = false
                            for (subansamblu in subansambleAtelier2) {
                                val idAgregat = subansamblu["reperId"]?.toString() ?: ""
                                val limitaDedicata = limiteAgregateDedicat[idAgregat] ?: 0
                                val curentAsamblat = capacitatiAgregateFinale[idAgregat] ?: 0
                                if (curentAsamblat < limitaDedicata) {
                                    val hartaConsumPiesePartajate = consumPartajatPerSubansamblu[idAgregat] ?: emptyMap()
                                    var areToatePieselePartajatePeStoc = true
                                    for ((codPiesaPartajata, necesarUnitate) in hartaConsumPiesePartajate) {
                                        val stocRamasPeRaft = stocPieseComunePeRaft[codPiesaPartajata] ?: 0
                                        if (stocRamasPeRaft < necesarUnitate) {
                                            areToatePieselePartajatePeStoc = false
                                        }
                                    }
                                    if (areToatePieselePartajatePeStoc) {
// Scădem stocul în mod dinamic pentru piesa partajată
                                        for ((codPiesaPartajata, necesarUnitate) in hartaConsumPiesePartajate) {
                                            val stocActual = stocPieseComunePeRaft[codPiesaPartajata] ?: 0
                                            stocPieseComunePeRaft[codPiesaPartajata] = stocActual - necesarUnitate
                                        }
                                        capacitatiAgregateFinale[idAgregat] = curentAsamblat + 1
                                        saEfectuatOAsamblareNoua = true
                                    }
                                }
                            }
                        }
                        for (subansamblu in subansambleAtelier2) {
                            val idAgregat = subansamblu["reperId"]?.toString() ?: ""
                            val numeAgregat = subansamblu["denumirePiesa"]?.toString() ?: idAgregat
                            val capacitateAgregatCalculata = capacitatiAgregateFinale[idAgregat] ?: 0
                            val detaliiAfisajLiniiLucru = detaliiPiese[idAgregat]?.toMutableList() ?: mutableListOf()
                            val hartaConsumPartajat = consumPartajatPerSubansamblu[idAgregat] ?: emptyMap()
                            for (codPiesaPartajata in hartaConsumPartajat.keys) {
                                val piesaInfoBOM = retetaBOM
                                    .map { (it as? Map<*, *>) ?: emptyMap<Any, Any>() }
                                    .firstOrNull { it["reperId"]?.toString() == codPiesaPartajata }
                                val numePiesaPartajata = piesaInfoBOM?.get("denumirePiesa")?.toString() ?: codPiesaPartajata
                                val stocRamasDupaImpartire = stocPieseComunePeRaft[codPiesaPartajata] ?: 0
                                detaliiAfisajLiniiLucru.add("      🔗 $numePiesaPartajata (Piesă Comună) Alocare dinamică live | Rămășiță raft: $stocRamasDupaImpartire buc")
                            }
                            CasetaCapacitateVizuala(
                                titruComponenta = numeAgregat,
                                capacitate = capacitateAgregatCalculata,
                                detaliiLinii = detaliiAfisajLiniiLucru,
                                etichetaUnitate = "AGREGATE"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(16.dp))
// ================= PANEL 2: ATELIERUL 4 (AMBALARE & PRODUS FINIT) =================
                    Text(text = "📦 SECTIA 4 • CAPACITATE PREDICTIVĂ FINISARE & AMBALARE (PRODUS FINIT):", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF004D40))
                    Spacer(modifier = Modifier.height(8.dp))
                    if (componenteAtelier4.isEmpty()) {
                        Text(text = "   • Nu sunt configurate elemente finale de asamblare în nomenclator.", fontSize = 13.sp, color = Color.Gray)
                    } else {
                        var capacitateScaunFinit = Int.MAX_VALUE
                        val detaliiScaunFinit = mutableListOf<String>()

                        for (reperFinal in componenteAtelier4) {
                            val codFinal = reperFinal["reperId"]?.toString() ?: ""
                            val numeFinal = reperFinal["denumirePiesa"]?.toString() ?: codFinal
                            val atelierProvenienta = reperFinal["atelierAsociat"]?.toString() ?: "Atelier4"
                            val necesarScaun = (reperFinal["cantitateNecesara"] as? Long)?.toInt() ?: 1
                            val idDocStocFinal = "${codFinal}_$atelierProvenienta"
                            val stocFirebaseFinal = listaStocuriFaze.find { it["idDoc"]?.toString() == idDocStocFinal }
                            val cantRaftFinal = (stocFirebaseFinal?.get("stocDisponibil") as? Long)?.toInt() ?: 0
                            val maxScaunePosibile = cantRaftFinal / necesarScaun
                            capacitateScaunFinit = min(capacitateScaunFinit, maxScaunePosibile)
                            val etichetaTipSectie = when (atelierProvenienta) {
                                "Atelier2" -> "Stoc Subansamble Sectia 2"
                                "Atelier1" -> "Stoc Direct din Debitari Secția 1"
                                else -> "Stoc Accesorii Sectia 4"
                            }
                            detaliiScaunFinit.add("      ▪ $numeFinal ($etichetaTipSectie): $cantRaftFinal buc existente ➔ Suficient pentru $maxScaunePosibile scaune")
                        }
                        if (capacitateScaunFinit == Int.MAX_VALUE) capacitateScaunFinit = 0
                        CasetaCapacitateVizuala(
                            titruComponenta = "Scaun Complet Finisat, Tapițat și Ambalat",
                            capacitate = capacitateScaunFinit,
                            detaliiLinii = detaliiScaunFinit,
                            etichetaUnitate = "SCAUNE FINITE",
                            esteSectieFinala = true
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun CasetaCapacitateVizuala(
    titruComponenta: String,
    capacitate: Int,
    detaliiLinii: List<String>,

    etichetaUnitate: String,
    esteSectieFinala: Boolean = false
) {
    val culoareFundal = if (capacitate > 0) {
        if (esteSectieFinala) Color(0xFFE0F2F1) else Color(0xFFECEFF1)
    } else {
        Color(0xFFFFEBEE)
    }
    val culoareInsigna = if (capacitate > 0) {
        if (esteSectieFinala) Color(0xFF00796B) else Color(0xFF455A64)
    } else {
        Color(0xFFD32F2F)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = culoareFundal)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, culoareInsigna.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titruComponenta,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.5.sp,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(culoareInsigna, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "$capacitate $etichetaUnitate",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))
            detaliiLinii.forEach { linie ->
                Text(
                    text = linie,
                    fontSize = 12.sp,
                    color = Color(0xFF424242),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

        }
    }
}