package com.example.sistem_gestiune_fabrica.screens

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
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaportIstoricScreen(
    firestore: FirebaseFirestore,
    userData: Map<String, Any>,
    onBackClick: () -> Unit
) {
    var listaToatePlanurileActive by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var istoricProductieTotal by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var seIncarca by remember { mutableStateOf(true) }
    var statusMesajText by remember { mutableStateOf("") }
    var filtruTimpCurent by remember { mutableStateOf("TOTAL") }
    var extindeVizualizareIstoricDetaliu by remember { mutableStateOf(false) }

    val drepturiFerestre = userData["drepturiFerestre"] as? Map<String, Any> ?: mapOf<String, Any>()
    val esteAdministrator = drepturiFerestre["administrare_drepturi"] as? Boolean ?: false


    val calendarAcum = remember { Calendar.getInstance() }
    var saptamanaSelectataNumar by remember { mutableStateOf(calendarAcum.get(Calendar.WEEK_OF_YEAR)) }
    var lunaSelectataNumar by remember { mutableStateOf(calendarAcum.get(Calendar.MONTH)) }
    var anSelectatNumar by remember { mutableStateOf(calendarAcum.get(Calendar.YEAR)) }
    val denumiriLuni = listOf("Ianuarie", "Februarie", "Martie", "Aprilie", "Mai", "Iunie", "Iulie", "August", "Septembrie", "Octombrie", "Noiembrie", "Decembrie")

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaToatePlanurileActive = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }.filter { it["stare"]?.toString() == "In Lucru" }
            }
        }
        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                istoricProductieTotal = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDocRaport" to it.id) }.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
                seIncarca = false
            }
        }
    }

    val istoricFiltratDinamice = remember(istoricProductieTotal, filtruTimpCurent, saptamanaSelectataNumar, lunaSelectataNumar, anSelectatNumar) {
        val calItem = Calendar.getInstance()
        istoricProductieTotal.filter { rasp ->
            val timestampMilis = (rasp["timestamp"] as? Long) ?: 0L
            if (timestampMilis == 0L) return@filter false
            calItem.timeInMillis = timestampMilis
            val anProd = calItem.get(Calendar.YEAR)
            when (filtruTimpCurent) {
                "SAPTAMANA" -> {
                    val saptProd = calItem.get(Calendar.WEEK_OF_YEAR)
                    anProd == anSelectatNumar && saptProd == saptamanaSelectataNumar
                }
                "LUNA" -> {
                    val lunaProd = calItem.get(Calendar.MONTH)
                    anProd == anSelectatNumar && lunaProd == lunaSelectataNumar
                }
                else -> true
            }
        }
    }

    Scaffold(
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color.LightGray.copy(alpha = 0.4f)).padding(16.dp), contentAlignment = Alignment.Center) {
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
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(text = "Realizări Live Calendar 📊", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color(0xFF00838F))
                Text("Vizualizarea cantităților realizate defalcat pe fiecărui reper", fontSize = 13.sp, color = Color.Gray)
                Divider(color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "📅 MOD AFISARE TIMP:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = filtruTimpCurent == "TOTAL", onClick = { filtruTimpCurent = "TOTAL"; extindeVizualizareIstoricDetaliu = false }, label = { Text("LIVE TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                            FilterChip(selected = filtruTimpCurent == "SAPTAMANA", onClick = { filtruTimpCurent = "SAPTAMANA"; if (esteAdministrator) extindeVizualizareIstoricDetaliu = true }, label = { Text("SĂPTĂMÂNĂ", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                            FilterChip(selected = filtruTimpCurent == "LUNA", onClick = { filtruTimpCurent = "LUNA"; if (esteAdministrator) extindeVizualizareIstoricDetaliu = true }, label = { Text("LUNĂ", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                        }

                        if (filtruTimpCurent == "SAPTAMANA") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { saptamanaSelectataNumar = if (saptamanaSelectataNumar > 1) saptamanaSelectataNumar - 1 else 52 }) { Text("◀ Săpt. Ant.", fontSize = 11.sp) }
                                Text(text = "SĂPTĂMÂNA nr. $saptamanaSelectataNumar", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF00838F))
                                OutlinedButton(onClick = { saptamanaSelectataNumar = if (saptamanaSelectataNumar < 52) saptamanaSelectataNumar + 1 else 1 }) { Text("Săpt. Urm. ▶", fontSize = 11.sp) }
                            }
                        }

                        if (filtruTimpCurent == "LUNA") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { lunaSelectataNumar = if (lunaSelectataNumar > 0) lunaSelectataNumar - 1 else 11 }) { Text("◀ Luna Ant.", fontSize = 11.sp) }
                                Text(text = denumiriLuni[lunaSelectataNumar].uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF00838F))
                                OutlinedButton(onClick = { lunaSelectataNumar = if (lunaSelectataNumar < 11) lunaSelectataNumar + 1 else 0 }) { Text("Luna Urm. ▶", fontSize = 11.sp) }
                            }
                        }

                        if (esteAdministrator) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { extindeVizualizareIstoricDetaliu = !extindeVizualizareIstoricDetaliu }) {
                                Checkbox(checked = extindeVizualizareIstoricDetaliu, onCheckedChange = { extindeVizualizareIstoricDetaliu = it })
                                Text("Afișează registrul de pontaj detaliat (Mod Audit Admin)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF455A64))
                            }
                        }
                    }
                }
            }

            if (statusMesajText.isNotEmpty()) {
                item { Text(text = statusMesajText, color = Color(0xFF198754), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }

            if (seIncarca) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF00838F)) } }
            }

            if (esteAdministrator && extindeVizualizareIstoricDetaliu) {item { Text(text = "📋 Registru Mod Audit: Intrări live din baza de date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
                items(istoricFiltratDinamice) { raport ->
                    val idDocRaport = raport["idDocRaport"]?.toString() ?: ""
                    val rArticol = raport["articolId"]?.toString() ?: ""
                    val rAtelier = raport["atelier"]?.toString() ?: "Atelier1"
                    val rQty = (raport["cantitateOk"] as? Long)?.toInt() ?: 0
                    val rUser = raport["operator"]?.toString() ?: "Operator"
                    val dataFrumoasa = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date((raport["timestamp"] as? Long) ?: 0L))
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFDC3545).copy(alpha = 0.2f), RoundedCornerShape(10.dp)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Cod Reper: ${rArticol.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "Sectie: $rAtelier | Cantitate: $rQty buc | Operator: $rUser", fontSize = 12.sp, color = Color.DarkGray)
                                Text(text = "Dată înregistrare: $dataFrumoasa", fontSize = 11.sp, color = Color.Gray)
                            }
                            Button(
                                onClick = {
                                    if (idDocRaport.isEmpty()) return@Button
                                    firestore.collection("istoric_productie").document(idDocRaport).delete().addOnSuccessListener {
                                        val idDocStocFazaCuSufix = "${rArticol}_$rAtelier"
                                        // Căutăm documentul de stoc cu sufixul atelierului, dacă nu există, încercăm fără sufix
                                        firestore.collection("stocuri_faze").document(idDocStocFazaCuSufix).get().addOnSuccessListener { d ->
                                            if (d.exists()) {
                                                val stocDisponibilAcum = d.getLong("stocDisponibil") ?: 0L
                                                firestore.collection("stocuri_faze").document(idDocStocFazaCuSufix)
                                                    .set(mapOf("stocDisponibil" to (stocDisponibilAcum - rQty)), SetOptions.merge())
                                                    .addOnSuccessListener { statusMesajText = "Succes! Raportarea anulată, stocul a fost recalculat!" }
                                            } else {
                                                // Încercăm fără sufixul atelierului
                                                val idDocStocFazaFaraSufix = rArticol
                                                firestore.collection("stocuri_faze").document(idDocStocFazaFaraSufix).get().addOnSuccessListener { d2 ->
                                                    val stocDisponibilAcum = d2.getLong("stocDisponibil") ?: 0L
                                                    firestore.collection("stocuri_faze").document(idDocStocFazaFaraSufix)
                                                        .set(mapOf("stocDisponibil" to (stocDisponibilAcum - rQty)), SetOptions.merge())
                                                        .addOnSuccessListener { statusMesajText = "Succes! Raportarea anulată, stocul a fost recalculat!" }
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)), shape = RoundedCornerShape(6.dp)
                            ) { Text("Anulează 🗑️", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            } else {
                items(listaToatePlanurileActive) { lot ->
                    val modelId = lot["modelId"]?.toString() ?: ""
                    val modelNume = lot["modelNume"]?.toString() ?: "Scaun"
                    val cantitateTintaScaune = (lot["cantitateScaune"] as? Long)?.toInt() ?: 0
                    val retetaBOM = lot["retetaBOM"] as? List<*> ?: listOf<Any>()
                    Card(modifier = Modifier.fillMaxWidth().border(width = 1.dp, Color(0xFFE5E5E5), RoundedCornerShape(size = 14.dp)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = modelNume.uppercase(), fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color(0xFF004D40))
                                val etichetaDinamicaText = when(filtruTimpCurent) { "SAPTAMANA" -> "Producție Săptămâna $saptamanaSelectataNumar" "LUNA" -> "Producție ${denumiriLuni[lunaSelectataNumar]}" else -> "Țintă Lot Global" }
                                Text(text = "$etichetaDinamicaText ($cantitateTintaScaune buc)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val ateliereConfig = listOf("Atelier0" to "⚙️ Atelier 0 - Secția Prelucrare Opțională", "Atelier1" to "🪓 Atelier 1 - Sectia Debitări Brute", "Atelier2" to "🔨 Atelier 2 - Montaj Subansamble", "Atelier3" to "🎨 Atelier 3 - Finisaje & Vopsitorie", "Atelier4" to "📦 Atelier 4 - Tapițerie & Ambalare Finală")
                            for ((atCod, atDenumire) in ateliereConfig) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp)).padding(6.dp)) { Text(text = atDenumire.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray) }
                                Spacer(modifier = Modifier.height(6.dp))
                                val repereSectie = retetaBOM.map { (it as? Map<String, Any>) ?: mapOf<String, Any>() }.filter { it["atelierAsociat"]?.toString() == atCod }
                                if (atCod == "Atelier4") {
                                    val realizatScaune = istoricFiltratDinamice.filter { it["articolId"]?.toString() == modelId && it["atelier"]?.toString() == "Atelier4" }.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
                                    val pro = if (cantitateTintaScaune > 0) (realizatScaune.toFloat() / cantitateTintaScaune).coerceIn(0f, 1f) else 0f
                                    ItemFilaRaportComponenta(componentaNume = "Scaun Complet Ambalat (Produs Finit)", codReper = modelId, realizat = realizatScaune, tinta = cantitateTintaScaune, procent = pro)
                                } else if (repereSectie.isEmpty()) {
                                    Text(text = "   • Nu sunt piese configurate în rețetă.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
                                } else {
                                    for (comp in repereSectie) {
                                        val rId = comp["reperId"]?.toString() ?: "unknown"
                                        val dNume = comp["denumirePiesa"]?.toString() ?: rId
                                        val deConsumatPerScaun = (comp["cantitateNecesara"] as? Long)?.toInt() ?: 1
                                        val subansambluDest = comp["subansambluDestinatie"]?.toString() ?: ""
                                        val tintaPiesaId = cantitateTintaScaune * deConsumatPerScaun
                                        val realizatPiesaId = istoricFiltratDinamice.filter { it["articolId"]?.toString() == rId && it["atelier"]?.toString() == atCod }.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
                                        val proPiesa = if (tintaPiesaId > 0) (realizatPiesaId.toFloat() / tintaPiesaId).coerceIn(0f, 1f) else 0f
                                        ItemFilaRaportComponenta(componentaNume = if (subansambluDest.isNotEmpty()) "$dNume (Destinație: $subansambluDest)" else dNume, codReper = rId, realizat = realizatPiesaId, tinta = tintaPiesaId, procent = proPiesa)
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
fun ItemFilaRaportComponenta(componentaNume: String, codReper: String, realizat: Int, tinta: Int, procent: Float) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1.4f)) {
            Text(text = componentaNume, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212529))
            Text(text = "Cod: $codReper", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(progress = procent, modifier = Modifier.fillMaxWidth().height(5.dp), trackColor = Color(0xFFE2E8F0), color = if (procent >= 1f) Color(0xFF2E7D32) else Color(0xFF00838F))
        }
        Column(modifier = Modifier.weight(0.8f), horizontalAlignment = Alignment.End) {
            Text(text = "$realizat / $tinta buc", fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (procent >= 1f) Color(0xFF2E7D32) else Color.DarkGray)
            Text(text = "${(procent * 100).toInt()}% gata", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (procent >= 1f) Color(0xFF2E7D32) else Color(0xFF00838F))
        }
    }
}