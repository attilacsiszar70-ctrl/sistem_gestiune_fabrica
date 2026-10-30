package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventariereScreen(
    firestore: FirebaseFirestore,
    userData: Map<String, Any>,
    onBackClick: () -> Unit
) {
    val usernameCurent = userData["username"]?.toString() ?: "unknown"
    val drepturiFerestre = userData["drepturiFerestre"] as? Map<String, Any> ?: mapOf<String, Any>()
    val esteAdministrator = drepturiFerestre["administrare_drepturi"] as? Boolean ?: false
    val functieUserText = userData["functie"]?.toString() ?: "Operator"
    val esteSefSauAdmin = functieUserText.uppercase().contains("ADMIN") || functieUserText.uppercase().contains("MAISTRU")

    var listaComenziActive by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaStocuriFaze by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var istoricRaportariPersonale by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var propuneriInventariere by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var atelierSelectat by remember { mutableStateOf<String?>(null) }
    var modelSelectat by remember { mutableStateOf<String?>(null) }
    var seIncarca by remember { mutableStateOf(true) }
    var mesajStatus by remember { mutableStateOf("") }

    val ateliereDisponibile = listOf("Atelier0", "Atelier1", "Atelier2", "Atelier3", "Atelier4")

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaComenziActive = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("stocuri_faze").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaStocuriFaze = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                istoricRaportariPersonale = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
                    .sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
            }
        }
        firestore.collection("propuneri_inventariere").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                propuneriInventariere = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
                    .sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
            }
            seIncarca = false
        }
    }

    val stocuriFiltrate = remember(listaComenziActive, listaStocuriFaze, istoricRaportariPersonale, atelierSelectat, modelSelectat) {
        val lista = mutableListOf<StocInventariere>()

        listaComenziActive.forEach { comanda ->
            val starePlan = comanda["stare"]?.toString() ?: "In Lucru"
            if (starePlan == "In Lucru") {
                val modelNume = comanda["modelNume"]?.toString() ?: "Scaun"
                val modelId = comanda["modelId"]?.toString() ?: ""
                if (modelSelectat != null && modelNume != modelSelectat) return@forEach

                val retetaBOM = comanda["retetaBOM"] as? List<Any> ?: emptyList()

                retetaBOM.forEach { nod ->
                    val comp = nod as? Map<String, Any> ?: emptyMap()
                    val reperId = comp["reperId"]?.toString() ?: ""
                    val denumirePiesa = comp["denumirePiesa"]?.toString() ?: reperId.uppercase()
                    val atelierPiesa = comp["atelierAsociat"]?.toString() ?: "Atelier1"
                    val cantitateNecesaraPerScaun = (comp["cantitateNecesara"] as? Long)?.toInt() ?: 1
                    if (atelierSelectat != null && atelierPiesa != atelierSelectat) return@forEach

                    val idDocStoc = "${reperId}_$atelierPiesa"
                    val stocCurent = (listaStocuriFaze.find { it["idDoc"] == idDocStoc }?.get("stocDisponibil") as? Long)?.toInt() ?: 0
                    
                    // Calculăm stocul teoretic: realizat - consumat
                    val realizat = istoricRaportariPersonale
                        .filter { it["articolId"]?.toString() == reperId && it["atelier"]?.toString() == atelierPiesa }
                        .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
                    
                    // Calculăm consumul pe baza raportărilor din Atelier4 (ambalare)
                    val raportariAtelier4 = istoricRaportariPersonale
                        .filter { it["atelier"]?.toString() == "Atelier4" && (it["articolId"]?.toString() == modelId || it["modelId"]?.toString() == modelId) }
                        .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
                    
                    val consumat = raportariAtelier4 * cantitateNecesaraPerScaun
                    val stocTeoretic = realizat - consumat

                    lista.add(
                        StocInventariere(
                            reperId = reperId,
                            denumirePiesa = denumirePiesa,
                            atelier = atelierPiesa,
                            modelNume = modelNume,
                            stocTeoretic = stocTeoretic,
                            stocCurent = stocCurent,
                            diferenta = stocCurent - stocTeoretic
                        )
                    )
                }
            }
        }
        lista
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
                Text(text = "Inventariere Ateliere 📦", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color(0xFF00838F))
                Text("Verificare și ajustare stocuri per atelier și produs", fontSize = 13.sp, color = Color.Gray)
                Divider(color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "🔍 FILTRE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Filtru atelier
                        Text(text = "Atelier:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = atelierSelectat == null, onClick = { atelierSelectat = null }, label = { Text("TOATE", fontSize = 10.sp) })
                            FilterChip(selected = atelierSelectat == "Atelier0", onClick = { atelierSelectat = "Atelier0" }, label = { Text("Atelier0", fontSize = 10.sp) })
                            FilterChip(selected = atelierSelectat == "Atelier1", onClick = { atelierSelectat = "Atelier1" }, label = { Text("Atelier1", fontSize = 10.sp) })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = atelierSelectat == "Atelier2", onClick = { atelierSelectat = "Atelier2" }, label = { Text("Atelier2", fontSize = 10.sp) })
                            FilterChip(selected = atelierSelectat == "Atelier3", onClick = { atelierSelectat = "Atelier3" }, label = { Text("Atelier3", fontSize = 10.sp) })
                            FilterChip(selected = atelierSelectat == "Atelier4", onClick = { atelierSelectat = "Atelier4" }, label = { Text("Atelier4", fontSize = 10.sp) })
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Filtru model
                        val modeleDisponibile = listaComenziActive.mapNotNull { it["modelNume"]?.toString() }.distinct()
                        if (modeleDisponibile.isNotEmpty()) {
                            Text(text = "Model:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val jumatate = (modeleDisponibile.size + 1) / 2
                            val primaJumatate = modeleDisponibile.take(jumatate)
                            val aDouaJumatate = modeleDisponibile.drop(jumatate)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = modelSelectat == null, onClick = { modelSelectat = null }, label = { Text("TOATE", fontSize = 10.sp) })
                                primaJumatate.forEach { model ->
                                    FilterChip(selected = modelSelectat == model, onClick = { modelSelectat = model }, label = { Text(model, fontSize = 10.sp) })
                                }
                            }
                            if (aDouaJumatate.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    aDouaJumatate.forEach { model ->
                                        FilterChip(selected = modelSelectat == model, onClick = { modelSelectat = model }, label = { Text(model, fontSize = 10.sp) })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (mesajStatus.isNotEmpty()) {
                item { Text(text = mesajStatus, color = Color(0xFF198754), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }

            if (seIncarca) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF00838F)) } }
            }

            // Afișare propuneri în așteptare pentru administrator
            if (esteAdministrator) {
                val propuneriInAsteptare = propuneriInventariere.filter { it["status"]?.toString() == "in_asteptare" }
                if (propuneriInAsteptare.isNotEmpty()) {
                    item {
                        Text(text = "📋 Propuneri în așteptare (Confirmare Admin)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFFDC3545))
                    }
                    items(propuneriInAsteptare) { propunere ->
                        PropunereInventariereCard(propunere, firestore, onConfirm = {
                            mesajStatus = "Propunere confirmată! Stocurile au fost actualizate."
                        }, onReject = {
                            mesajStatus = "Propunere respinsă."
                        })
                    }
                }
            }

            // Afișare stocuri teoretice vs reale
            item {
                Text(text = "📊 Stocuri per Atelier și Produs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }

            if (stocuriFiltrate.isEmpty()) {
                item { Text(text = "Nu sunt date de afișat pentru filtrele selectate.", fontSize = 13.sp, color = Color.Gray) }
            }

            items(stocuriFiltrate) { stoc ->
                StocInventariereCard(stoc, usernameCurent, esteAdministrator, firestore) { mesajStatus = it }
            }
        }
    }
}

@Composable
fun StocInventariereCard(
    stoc: StocInventariere,
    username: String,
    esteAdmin: Boolean,
    firestore: FirebaseFirestore,
    onMesajStatus: (String) -> Unit
) {
    var cantitatePropusa by remember { mutableStateOf("") }
    var arataInput by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stoc.denumirePiesa, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212529))
                    Text(text = "${stoc.modelNume} | ${stoc.atelier}", fontSize = 14.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Stoc fizic", fontSize = 15.sp, color = Color.Gray)
                    Text(text = "${stoc.stocCurent} buc", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00838F))
                }
            }
            if (arataInput) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cantitatePropusa,
                    onValueChange = { cantitatePropusa = it },
                    label = { Text("Cantitate reală inventariată") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { arataInput = false }, modifier = Modifier.weight(1f)) { Text("Anulează", fontSize = 11.sp) }
                    Button(
                        onClick = {
                            val cantitate = cantitatePropusa.toIntOrNull()
                            if (cantitate != null) {
                                val propunere = mapOf(
                                    "reperId" to stoc.reperId,
                                    "denumirePiesa" to stoc.denumirePiesa,
                                    "atelier" to stoc.atelier,
                                    "modelNume" to stoc.modelNume,
                                    "stocVechi" to stoc.stocCurent,
                                    "stocNou" to cantitate,
                                    "stocTeoretic" to stoc.stocTeoretic,
                                    "diferenta" to (cantitate - stoc.stocCurent),
                                    "propusDe" to username,
                                    "timestamp" to System.currentTimeMillis(),
                                    "status" to "in_asteptare"
                                )
                                firestore.collection("propuneri_inventariere").add(propunere)
                                    .addOnSuccessListener {
                                        arataInput = false
                                        cantitatePropusa = ""
                                        onMesajStatus("Propunere trimisă spre aprobare!")
                                    }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Trimite Propunere", fontSize = 11.sp) }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { arataInput = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C757D))
                ) { Text("Propune Ajustare", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun PropunereInventariereCard(
    propunere: Map<String, Any>,
    firestore: FirebaseFirestore,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val reperId = propunere["reperId"]?.toString() ?: ""
    val denumire = propunere["denumirePiesa"]?.toString() ?: reperId
    val atelier = propunere["atelier"]?.toString() ?: ""
    val model = propunere["modelNume"]?.toString() ?: ""
    val stocVechi = (propunere["stocVechi"] as? Long)?.toInt() ?: 0
    val stocNou = (propunere["stocNou"] as? Long)?.toInt() ?: 0
    val propusDe = propunere["propusDe"]?.toString() ?: "Unknown"
    val timestamp = (propunere["timestamp"] as? Long) ?: 0L
    val dataFormatata = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timestamp))
    val idDoc = propunere["idDoc"]?.toString() ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
        border = BorderStroke(1.dp, Color(0xFFFFC107))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = denumire, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = "$model | $atelier", fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Teoretic", fontSize = 10.sp, color = Color.Gray)
                    Text(text = "$stocVechi buc", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Propus", fontSize = 10.sp, color = Color.Gray)
                    Text(text = "$stocNou buc", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00838F))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Diferență", fontSize = 10.sp, color = Color.Gray)
                    Text(text = "${stocNou - stocVechi} buc", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Propus de: $propusDe | $dataFormatata", fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Actualizează stocul
                        val idDocStoc = "${reperId}_$atelier"
                        firestore.collection("stocuri_faze").document(idDocStoc).get().addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                firestore.collection("stocuri_faze").document(idDocStoc)
                                    .set(mapOf("stocDisponibil" to stocNou.toLong()), SetOptions.merge())
                                    .addOnSuccessListener {
                                        // Marchează propunerea ca confirmată
                                        firestore.collection("propuneri_inventariere").document(idDoc)
                                            .set(mapOf("status" to "confirmat", "confirmatDe" to "Admin", "timestampConfirmare" to System.currentTimeMillis()), SetOptions.merge())
                                            .addOnSuccessListener { onConfirm() }
                                    }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754))
                ) { Text("Confirmă", fontSize = 11.sp) }
                Button(
                    onClick = {
                        firestore.collection("propuneri_inventariere").document(idDoc)
                            .set(mapOf("status" to "respins", "respinsDe" to "Admin", "timestampRespingere" to System.currentTimeMillis()), SetOptions.merge())
                            .addOnSuccessListener { onReject() }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545))
                ) { Text("Respinge", fontSize = 11.sp) }
            }
        }
    }
}

data class StocInventariere(
    val reperId: String,
    val denumirePiesa: String,
    val atelier: String,
    val modelNume: String,
    val stocTeoretic: Int,
    val stocCurent: Int,
    val diferenta: Int
)
