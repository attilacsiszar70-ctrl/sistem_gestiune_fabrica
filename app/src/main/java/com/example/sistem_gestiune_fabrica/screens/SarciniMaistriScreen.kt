package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

data class SarcinaMaistru(
    val reperId: String,
    val denumirePiesa: String,
    val atelier: String,
    val modelNume: String,
    val stocCurent: Int,
    val cantitateAmbalata: Int,
    val tintaPlan: Int,
    val ramas: Int,
    val procent: Float,
    val prioritate: String // "CRITIC", "URGENT", "NORMAL"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SarciniMaistriScreen(
    firestore: FirebaseFirestore,
    userData: Map<String, Any>,
    onBackClick: () -> Unit
) {
    val usernameCurent = userData["username"]?.toString() ?: "unknown"
    val drepturiAteliere = userData["drepturiAteliere"] as? Map<String, Any> ?: emptyMap()
    val functieUserText = userData["functie"]?.toString() ?: "Operator"
    val esteSefSauAdmin = functieUserText.uppercase().contains("ADMIN") || functieUserText.uppercase().contains("MAISTRU")

    val ateliereUtilizator = listOf("Atelier0", "Atelier1", "Atelier2", "Atelier3", "Atelier4").filter {
        esteSefSauAdmin || drepturiAteliere[it] == true || drepturiAteliere[it]?.toString() == "true"
    }

    var listaComenziActive by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaStocuriFaze by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var istoricRaportariPersonale by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var prioritateSelectata by remember { mutableStateOf<String?>(null) }
    var atelierSelectat by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) listaComenziActive = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
        }
        firestore.collection("stocuri_faze").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) listaStocuriFaze = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
        }
        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                istoricRaportariPersonale = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
                    .sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
            }
        }
    }

    val sarciniMaistru = remember(listaComenziActive, listaStocuriFaze, ateliereUtilizator, istoricRaportariPersonale) {
        val listaSarcini = mutableListOf<SarcinaMaistru>()

        listaComenziActive.forEach { comanda ->
            val starePlan = comanda["stare"]?.toString() ?: "In Lucru"
            if (starePlan == "In Lucru") {
                val cantitateScauneFinale = (comanda["cantitateScaune"] as? Long)?.toInt() ?: 0
                val modelNumeScaun = comanda["modelNume"]?.toString() ?: "Scaun"
                val retetaBOM = comanda["retetaBOM"] as? List<Any> ?: emptyList()

                retetaBOM.forEach { nod ->
                    val comp = nod as? Map<String, Any> ?: emptyMap()
                    val reperId = comp["reperId"]?.toString() ?: ""
                    val denumirePiesa = comp["denumirePiesa"]?.toString() ?: reperId.uppercase()
                    val atelierPiesa = comp["atelierAsociat"]?.toString() ?: "Atelier1"
                    val cantitateNecesaraPerScaun = (comp["cantitateNecesara"] as? Long)?.toInt() ?: 1

                    val tintaTotalaPiesa = cantitateScauneFinale * cantitateNecesaraPerScaun
                    val stocIdDoc = "${reperId}_$atelierPiesa"

                    // Căutăm stocul cu sufixul atelierului, dacă nu găsim, încercăm fără sufix
                    val documentStoc = listaStocuriFaze.find { it["idDoc"] == stocIdDoc }
                        ?: listaStocuriFaze.find { it["idDoc"] == reperId }
                    val stocCurent = (documentStoc?.get("stocDisponibil") as? Long)?.toInt() ?: 0
                    val realizatCumulat = istoricRaportariPersonale
                        .filter { it["articolId"]?.toString() == reperId && it["atelier"]?.toString() == atelierPiesa }
                        .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }

                    val ramas = (tintaTotalaPiesa - realizatCumulat).coerceAtLeast(0)
                    val procent = if (tintaTotalaPiesa > 0) (realizatCumulat.toFloat() / tintaTotalaPiesa * 100).coerceAtMost(100f) else 0f

                    // Calculare progres efectiv (realizat + stoc disponibil)
                    val totalDisponibil = realizatCumulat + stocCurent
                    val procentEfectiv = if (tintaTotalaPiesa > 0) (totalDisponibil.toFloat() / tintaTotalaPiesa * 100).coerceAtMost(100f) else 0f

                    // Calculare prioritate pe baza progresului efectiv și a stocului
                    val prioritate = when {
                        stocCurent < 0 -> "CRITIC" // Stoc negativ = blocaj critic
                        stocCurent == 0 && ramas > 0 -> "CRITIC" // Fără stoc dar mai e de produs
                        procentEfectiv >= 100f -> "NORMAL" // Avem suficient pe stoc
                        procentEfectiv < 25f -> "CRITIC"
                        procentEfectiv < 50f -> "URGENT"
                        else -> "NORMAL"
                    }

                    if (ateliereUtilizator.contains(atelierPiesa) && reperId.isNotEmpty()) {
                        listaSarcini.add(
                            SarcinaMaistru(
                                reperId = reperId,
                                denumirePiesa = denumirePiesa,
                                atelier = atelierPiesa,
                                modelNume = modelNumeScaun,
                                stocCurent = stocCurent,
                                cantitateAmbalata = 0,
                                tintaPlan = tintaTotalaPiesa,
                                ramas = ramas,
                                procent = procentEfectiv,
                                prioritate = prioritate
                            )
                        )
                    }
                }

                // Adaugăm și produsul finit (Atelier4)
                val modelIdScaun = comanda["modelId"]?.toString() ?: ""
                val atelierScaunFinal = comanda["atelierAsociat"]?.toString() ?: "Atelier4"
                if (modelIdScaun.isNotEmpty() && ateliereUtilizator.contains(atelierScaunFinal)) {
                    val stocIdDoc = "${modelIdScaun}_$atelierScaunFinal"
                    val documentStoc = listaStocuriFaze.find { it["idDoc"] == stocIdDoc }
                    val stocCurent = (documentStoc?.get("stocDisponibil") as? Long)?.toInt() ?: 0
                    val realizatScaune = istoricRaportariPersonale
                        .filter { it["articolId"]?.toString() == modelIdScaun && it["atelier"]?.toString() == atelierScaunFinal }
                        .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }

                    val ramas = (cantitateScauneFinale - realizatScaune).coerceAtLeast(0)
                    val procent = if (cantitateScauneFinale > 0) (realizatScaune.toFloat() / cantitateScauneFinale * 100).coerceAtMost(100f) else 0f

                    // Pentru produsul finit (Atelier4), includem în stoc și cantitatea ambalată recent
                    // Stocul efectiv = stoc curent + realizat (produsele ambalate sunt pe stoc)
                    val stocEfectiv = stocCurent + realizatScaune
                    val procentEfectiv = if (cantitateScauneFinale > 0) (stocEfectiv.toFloat() / cantitateScauneFinale * 100).coerceAtMost(100f) else 0f

                    // Calculare prioritate pe baza progresului efectiv
                    val prioritate = when {
                        procentEfectiv >= 100f -> "NORMAL" // Avem suficient pe stoc
                        procentEfectiv < 25f -> "CRITIC"
                        procentEfectiv < 50f -> "URGENT"
                        else -> "NORMAL"
                    }

                    listaSarcini.add(
                        SarcinaMaistru(
                            reperId = modelIdScaun,
                            denumirePiesa = "Scaun Complet Ambalat",
                            atelier = atelierScaunFinal,
                            modelNume = modelNumeScaun,
                            stocCurent = stocCurent,
                            cantitateAmbalata = realizatScaune,
                            tintaPlan = cantitateScauneFinale,
                            ramas = ramas,
                            procent = procentEfectiv,
                            prioritate = prioritate
                        )
                    )
                }
            }
        }

        listaSarcini
    }

    // Filtrare
    val sarciniFiltrate = remember(sarciniMaistru, prioritateSelectata, atelierSelectat) {
        sarciniMaistru.filter { sarcina ->
            val potrivestePrioritate = prioritateSelectata == null || sarcina.prioritate == prioritateSelectata
            val potrivesteAtelier = atelierSelectat == null || sarcina.atelier == atelierSelectat
            potrivestePrioritate && potrivesteAtelier
        }.sortedWith(compareBy({ it.prioritate }, { it.atelier }))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- ZONĂ FIXĂ (NU SCROLLABILĂ) ---
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sarcini Maistri", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Cont activ: $usernameCurent pe sectoarele $ateliereUtilizator", fontSize = 11.sp, color = Color.Gray)
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

            // --- FILTRE PRIORITATE ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = prioritateSelectata == null,
                    onClick = { prioritateSelectata = null },
                    label = { Text("TOATE", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = prioritateSelectata == "CRITIC",
                    onClick = { prioritateSelectata = if (prioritateSelectata == "CRITIC") null else "CRITIC" },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFDC3545),
                        selectedLabelColor = Color.White
                    ),
                    label = { Text("CRITIC 🔴", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = prioritateSelectata == "URGENT",
                    onClick = { prioritateSelectata = if (prioritateSelectata == "URGENT") null else "URGENT" },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFBC02D),
                        selectedLabelColor = Color.Black
                    ),
                    label = { Text("URGENT 🟡", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = prioritateSelectata == "NORMAL",
                    onClick = { prioritateSelectata = if (prioritateSelectata == "NORMAL") null else "NORMAL" },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF198754),
                        selectedLabelColor = Color.White
                    ),
                    label = { Text("NORMAL 🟢", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            // --- FILTRE ATELIERE ---
            val ateliereDisponibile = ateliereUtilizator.sorted()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = atelierSelectat == null,
                    onClick = { atelierSelectat = null },
                    label = { Text("TOATE ATELIERE", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                ateliereDisponibile.forEach { atelier ->
                    val numeScurt = when(atelier) {
                        "Atelier0" -> "ATELIER 0"
                        "Atelier1" -> "ATELIER 1"
                        "Atelier2" -> "ATELIER 2"
                        "Atelier3" -> "ATELIER 3"
                        "Atelier4" -> "ATELIER 4"
                        else -> atelier.uppercase()
                    }
                    FilterChip(
                        selected = atelierSelectat == atelier,
                        onClick = { atelierSelectat = if (atelierSelectat == atelier) null else atelier },
                        label = { Text(numeScurt, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // --- ZONĂ SCROLLABILĂ ---
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sarciniFiltrate.isEmpty()) {
                item { Text("Nu există sarcini de afișat.", color = Color.Gray, fontSize = 13.sp) }
            }

            items(sarciniFiltrate) { sarcina ->
                val culoarePrioritate = when(sarcina.prioritate) {
                    "CRITIC" -> Color(0xFFDC3545)
                    "URGENT" -> Color(0xFFFBC02D)
                    else -> Color(0xFF198754)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = sarcina.modelNume.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(text = sarcina.denumirePiesa, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF212529))
                                Text(text = "Atelier: ${sarcina.atelier}", fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(
                                text = sarcina.prioritate,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.background(culoarePrioritate, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("ȚINTĂ", fontSize = 11.sp, color = Color.Gray); Text("${sarcina.tintaPlan} buc", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("REALIZAT", fontSize = 11.sp, color = Color.Gray); Text("${sarcina.tintaPlan - sarcina.ramas} buc", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF198754)) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("STOC", fontSize = 11.sp, color = Color.Gray); Text("${sarcina.stocCurent} buc", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (sarcina.stocCurent < 0) Color(0xFFDC3545) else Color(0xFF1976D2)) }
                            if (sarcina.atelier == "Atelier4") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("AMBALAT", fontSize = 11.sp, color = Color.Gray); Text("${sarcina.cantitateAmbalata} buc", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE65100)) }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("RĂMAS", fontSize = 11.sp, color = Color.Gray); Text("${sarcina.ramas} buc", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = culoarePrioritate) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("PROGRES", fontSize = 11.sp, color = Color.Gray); Text("${sarcina.procent.toInt()}%", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = culoarePrioritate) }
                        }
                    }
                }
            }
        }
    }
}
