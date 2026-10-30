package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AuditSaptamanalRow(
    val idDocument: String,
    val reperId: String,
    val fazaNume: String,
    val saptamanaEticheta: String,
    val stocFizic: Int
)

data class DetaliuRaportare(
    val reperId: String,
    val denumireComercialaReper: String,
    val modelProdusScaun: String,
    val cantitate: Int,
    val atelier: String,
    val oraText: String
)

data class AgregareUserInteractiveRow(
    val utilizator: String,
    val totalPieseRaportate: Int,
    val numarTranzactii: Int,
    val detaliiPiese: List<DetaliuRaportare>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditStocuriSaptamanalScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    var listaToateStocurileRaw by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaIstoricProductieRaw by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaComenziActive by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var filtruCautare by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(true) }

    var tabSelectat by remember { mutableStateOf(0) }
    var ziSelectataFiltru by remember { mutableStateOf("TOATE ZILELE") }
    var utilizatorExtinsAcum by remember { mutableStateOf("") }

    val formatData = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) }
    val formatOra = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        firestore.collection("stocuri_faze").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaToateStocurileRaw = snapshot.documents.map {
                    (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id)
                }
            }
        }

        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaComenziActive = snapshot.documents.map {
                    (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id)
                }
            }
        }

        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaIstoricProductieRaw = snapshot.documents.map {
                    (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id)
                }
            }
            seIncarca = false
        }
    }

    val toateZileleDisponibile = remember(listaIstoricProductieRaw) {
        listOf("TOATE ZILELE") + listaIstoricProductieRaw.map { doc ->
            val ts = (doc["timestamp"] as? Long) ?: 0L
            if (ts > 0) formatData.format(Date(ts)) else "Fără Dată"
        }.distinct().sortedByDescending { it }
    }

    val dateAuditSaptamana = remember(listaToateStocurileRaw, filtruCautare) {
        listaToateStocurileRaw.map { doc ->
            val idDoc = doc["idDoc"]?.toString() ?: ""
            val rId = doc["reperId"]?.toString() ?: idDoc.split("_").first()
            val faza = doc["faza"]?.toString() ?: "Monitorizare Stoc"
            val perioada = doc["perioada"]?.toString() ?: "Lunar / Permanent"
            val stoc = (doc["stocDisponibil"] as? Long)?.toInt() ?: 0
            AuditSaptamanalRow(idDoc, rId, faza, perioada, stoc)
        }.filter {
            filtruCautare.isBlank() || it.reperId.contains(filtruCautare, ignoreCase = true)
        }.sortedWith(compareBy({ it.reperId }, { it.saptamanaEticheta }))
    }

    val dateAuditUseriInteractiv = remember(listaIstoricProductieRaw, listaComenziActive, filtruCautare, ziSelectataFiltru) {
        val istoricFiltratPeZi = listaIstoricProductieRaw.filter { doc ->
            val ts = (doc["timestamp"] as? Long) ?: 0L
            val ziuaDoc = if (ts > 0) formatData.format(Date(ts)) else "Fără Dată"
            ziSelectataFiltru == "TOATE ZILELE" || ziuaDoc == ziSelectataFiltru
        }

        istoricFiltratPeZi.groupBy { doc ->
            doc["operator"]?.toString() ?: "Operator Anonim"
        }.map { (user, documenteUser) ->

            val detaliiTranzactii = documenteUser.map { d ->
                val ts = (d["timestamp"] as? Long) ?: 0L
                val articolIdRaw = (d["articolId"]?.toString() ?: "").trim()

                DetaliuRaportare(
                    reperId = articolIdRaw,
                    denumireComercialaReper = d["denumirePiesa"]?.toString() ?: "Componentă",
                    modelProdusScaun = articolIdRaw,
                    cantitate = (d["cantitateOk"] as? Long)?.toInt() ?: 0,
                    atelier = d["atelier"]?.toString() ?: "Secție",
                    oraText = if (ts > 0) formatOra.format(Date(ts)) else "--:--:--"
                )
            }.filter {
                filtruCautare.isBlank() ||
                        it.reperId.contains(filtruCautare, ignoreCase = true) ||
                        it.denumireComercialaReper.contains(filtruCautare, ignoreCase = true) ||
                        it.modelProdusScaun.contains(filtruCautare, ignoreCase = true)
            }.sortedByDescending { it.oraText }

            val totalPiese = detaliiTranzactii.sumOf { it.cantitate }
            AgregareUserInteractiveRow(user, totalPiese, detaliiTranzactii.size, detaliiTranzactii)
        }.filter { it.numarTranzactii > 0 }.sortedByDescending { it.totalPieseRaportate }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        // --- ANTET FIX PANOU ---
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Panou Trasabilitate PLIMOB 🔍", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Control extins pe zile, operatori și fișe tehnice", fontSize = 12.sp, color = Color.Gray)
                }
                FilledTonalIconButton(onClick = onBackClick, modifier = Modifier.size(50.dp)) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Înapoi")
                }
            }

            TabRow(selectedTabIndex = tabSelectat) {
                Tab(selected = tabSelectat == 0, onClick = { tabSelectat = 0 }) {
                    Text("📅 FIȘE SĂPTĂMÂNI", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                }
                Tab(selected = tabSelectat == 1, onClick = { tabSelectat = 1 }) {
                    Text("👤 ACTIVITATE OPERATORI", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                }
            }

            if (tabSelectat == 1) {
                var expandedDropdown by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Alege Ziua de Lucru:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { expandedDropdown = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "📆 $ziSelectataFiltru", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }) {
                            toateZileleDisponibile.forEach { ziItem ->
                                DropdownMenuItem(
                                    text = { Text(ziItem, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        ziSelectataFiltru = ziItem
                                        expandedDropdown = false
                                        utilizatorExtinsAcum = ""
                                    }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = filtruCautare,
                onValueChange = { filtruCautare = it },
                placeholder = { Text("🔍 Filtrează rapid după cod piesă din Firebase...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tabSelectat == 0) {
                    Text("COD REPER / COMPONENTĂ", modifier = Modifier.weight(1.5f), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("PERIOADĂ", modifier = Modifier.weight(1f), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("STOC FIZIC", modifier = Modifier.weight(1f), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                } else {
                    Text("ISTORIC DETALIAT TRANZACȚII OPERATORI", modifier = Modifier.fillMaxWidth(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
// --- ZONA SCROLLABILĂ INTERACTIVĂ ---
        if (seIncarca) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (tabSelectat == 0) {
                    items(dateAuditSaptamana) { row ->
                        val esteNegativ = row.stocFizic < 0
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(6.dp)).border(1.dp, if(esteNegativ) Color(0xFFDC3545) else Color(0xFFE5E5E5), RoundedCornerShape(6.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(text = row.reperId.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Text(text = row.fazaNume, fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(text = row.saptamanaEticheta, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
                            Text(text = "${row.stocFizic} buc", modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = if (esteNegativ) Color(0xFFDC3545) else Color(0xFF198754))
                        }
                    }
                } else {
                    items(dateAuditUseriInteractiv) { row ->
                        val esteExtins = utilizatorExtinsAcum == row.utilizator
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                utilizatorExtinsAcum = if (esteExtins) "" else row.utilizator
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = if(esteExtins) Color(0xFFFFFDE7) else Color.White),
                            border = BorderStroke(1.dp, if(esteExtins) MaterialTheme.colorScheme.primary else Color(0xFFE5E5E5))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text(text = row.utilizator.uppercase(), fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFFE65100))
                                        Text(text = if(esteExtins) "▼ Ascunde detaliile" else "▶ Click pentru a deschide piesele transmise", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text(text = "${row.numarTranzactii} loturi", modifier = Modifier.weight(0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.DarkGray)
                                    Text(text = "${row.totalPieseRaportate} buc", modifier = Modifier.weight(1.2f), fontSize = 15.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = Color(0xFF198754))
                                }
                                AnimatedVisibility(visible = esteExtins) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).background(Color(0xFFF8F9FA), RoundedCornerShape(6.dp)).padding(8.dp)) {
                                        Text(
                                            text = "FIȘĂ ACTIVITATE OPERATOR DIN DATA $ziSelectataFiltru:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        row.detaliiPiese.forEach { detaliu ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp)
                                            ) {
// 💡 RÂNDUL 1: ID-ul tehnic complet unificat fără nicio liniuță de subliniere ""
                                                val idFaraLiniute = detaliu.modelProdusScaun.replace("_", " ").lowercase()
                                                Text(
                                                    text = idFaraLiniute,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 2,
                                                    softWrap = false
                                                )
// 💡 RÂNDUL 2: Gruparea compactă a tuturor parametrilor asociați
                                                Text(
                                                    text = "Sect: ${detaliu.atelier}  | Oră: ${detaliu.oraText} | Cant: +${detaliu.cantitate} buc",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF212529),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                                )
                                                Divider(color = Color(0xFFE5E5E5), modifier = Modifier.padding(top = 6.dp))
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