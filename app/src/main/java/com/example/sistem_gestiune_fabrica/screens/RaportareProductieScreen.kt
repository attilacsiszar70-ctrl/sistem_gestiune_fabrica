package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


data class TotalizarePiesaStatus(
    val reperId: String,
    val denumirePiesa: String,
    val tintaPlan: Int,
    val realizat: Int,
    val ramas: Int,
    val procent: Float,
    val atelierAsociat: String,
    val modelNume: String,
    val idDocStoc: String,
    val retetaBOM: List<Any>,
    val fazaPrelucrare: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaportareProductieScreen(
    firestore: FirebaseFirestore,
    atelierInitialCod: String,
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
    var codAtelierSelectat by remember { mutableStateOf(atelierInitialCod) }
    var listaComenziActive by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaStocuriFaze by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var istoricRaportariPersonale by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var piesaSelectataCheieUnique by remember { mutableStateOf("") }
    var cantitateNouaText by remember { mutableStateOf("") }
    var numarOrdineFaza by remember { mutableStateOf(1) }
    var numeFazaSelectata by remember { mutableStateOf("Frezare / Profilare") }

    // --- CĂUTARE ȘI FILTRE INTELIGENTE PENTRU RAPIDITATE ---
    var textCautarePiesa by remember { mutableStateOf("") }
    var filtruModelSelectat by remember { mutableStateOf("TOATE") } // Filtru pe model/scaun
    var filtruIstoricPerioada by remember { mutableStateOf("ASTAZI") } // "ASTAZI", "ULTIMELE_3_ZILE", "SAPTAMANA", "TOATE"

    var tabSelectatAtelier0 by remember { mutableStateOf("LOTURI") }
    var denumireReperGenericText by remember { mutableStateOf("") }
    var fazaGenericaText by remember { mutableStateOf("") }
    var cantitateGenericaText by remember { mutableStateOf("") }

    var esteModEditareRaport by remember { mutableStateOf(false) }
    var idRaportEditat by remember { mutableStateOf("") }
    var cantitateRaportataAnterior by remember { mutableStateOf(0) }

    var mesajStatus by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(false) }
    val listState = rememberLazyListState() // 💡 Reține poziția de scroll a listei
    val coroutineScope = rememberCoroutineScope() // 💡 Necesar pentru a rula animația de scroll

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

    val pieseTotalizateAfisate = remember(listaComenziActive, listaStocuriFaze, ateliereUtilizator, istoricRaportariPersonale) {
        val matrixFlux = mutableListOf<TotalizarePiesaStatus>()

        listaComenziActive.forEach { comanda ->
            val starePlan = comanda["stare"]?.toString() ?: "In Lucru"
            if (starePlan == "In Lucru") {
                val cantitateScauneFinale = (comanda["cantitateScaune"] as? Long)?.toInt() ?: 0
                val modelNumeScaun = comanda["modelNume"]?.toString() ?: "Scaun"
                val retetaBOM = comanda["retetaBOM"] as? List<Any> ?: emptyList()
                val timestampLansareComanda = (comanda["timestampLansare"] as? Long) ?: 0L

                retetaBOM.forEach { nod ->
                    val comp = nod as? Map<String, Any> ?: emptyMap()
                    val reperId = comp["reperId"]?.toString() ?: ""
                    val denumirePiesa = comp["denumirePiesa"]?.toString() ?: reperId.uppercase()
                    val rawAtelierPiesa = comp["atelierAsociat"]?.toString() ?: "Atelier1"
                    // Normalizăm dacă este AtelierO (litera O) în loc de Atelier0 (cifra 0)
                    val atelierPiesa = if (rawAtelierPiesa.equals("AtelierO", ignoreCase = true)) "Atelier0" else rawAtelierPiesa
                    val cantitateNecesaraPerScaun = (comp["cantitateNecesara"] as? Long)?.toInt() ?: 1

                    val tintaTotalaPiesa = cantitateScauneFinale * cantitateNecesaraPerScaun

                    val fazaPrelucrareInBom = comp["fazaPrelucrare"]?.toString() ?: ""

                    val areDreptPeAtelier = ateliereUtilizator.any { it.equals(atelierPiesa, ignoreCase = true) || (atelierPiesa == "Atelier0" && it.equals("AtelierO", ignoreCase = true)) }
                    if (areDreptPeAtelier && reperId.isNotEmpty()) {
                        val idDocStoc = "${reperId}_$atelierPiesa"

                        // Realizările se cumulează strict de la momentul lansării lotului curent
                        val realizatCumulatIstoric = istoricRaportariPersonale
                            .filter {
                                it["articolId"]?.toString() == reperId &&
                                it["atelier"]?.toString() == atelierPiesa &&
                                ((it["timestamp"] as? Long) ?: 0L) >= timestampLansareComanda
                            }
                            .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }

                        // Căutăm stocul cu sufixul atelierului, dacă nu găsim, încercăm fără sufix
                        val stocFizicActualInDepozit = ((listaStocuriFaze.find { it["idDoc"] == idDocStoc }?.get("stocDisponibil") as? Long)?.toInt()
                            ?: (listaStocuriFaze.find { it["idDoc"] == reperId }?.get("stocDisponibil") as? Long)?.toInt()) ?: 0

                        val ramas = (tintaTotalaPiesa - realizatCumulatIstoric).coerceAtLeast(0)
                        val procent = if (tintaTotalaPiesa > 0) (realizatCumulatIstoric.toFloat() / tintaTotalaPiesa * 100).coerceAtMost(100f) else 0f

                        matrixFlux.add(
                            TotalizarePiesaStatus(
                                reperId = reperId,
                                denumirePiesa = denumirePiesa,
                                tintaPlan = tintaTotalaPiesa,
                                realizat = realizatCumulatIstoric,
                                ramas = ramas,
                                procent = procent,
                                atelierAsociat = atelierPiesa,
                                modelNume = "$modelNumeScaun (Pe Raft: $stocFizicActualInDepozit buc)",
                                idDocStoc = idDocStoc,
                                retetaBOM = retetaBOM,
                                fazaPrelucrare = fazaPrelucrareInBom
                            )
                        )
                    }
                }

                val atelierFinalScaun = comanda["atelierAsociat"]?.toString() ?: "Atelier4"
                val modelIdScaun = comanda["modelId"]?.toString() ?: ""
                if (ateliereUtilizator.contains(atelierFinalScaun) && modelIdScaun.isNotEmpty()) {
                    val idDocStoc = "${modelIdScaun}_$atelierFinalScaun"

                    // Realizările produsului finit se cumulează de la momentul lansării lotului curent
                    val realizatScauneIstoric = istoricRaportariPersonale
                        .filter {
                            it["articolId"]?.toString() == modelIdScaun &&
                            it["atelier"]?.toString() == atelierFinalScaun &&
                            ((it["timestamp"] as? Long) ?: 0L) >= timestampLansareComanda
                        }
                        .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }

                    val stocFizicScauneMags = (listaStocuriFaze.find { it["idDoc"] == idDocStoc }?.get("stocDisponibil") as? Long)?.toInt() ?: 0

                    val ramas = (cantitateScauneFinale - realizatScauneIstoric).coerceAtLeast(0)
                    val procent = if (cantitateScauneFinale > 0) (realizatScauneIstoric.toFloat() / cantitateScauneFinale * 100).coerceAtMost(100f) else 0f

                    matrixFlux.add(
                        TotalizarePiesaStatus(
                            reperId = modelIdScaun,
                            denumirePiesa = "Scaun Complet Ambalat",
                            tintaPlan = cantitateScauneFinale,
                            realizat = realizatScauneIstoric,
                            ramas = ramas,
                            procent = procent,
                            atelierAsociat = atelierFinalScaun,
                            modelNume = "$modelNumeScaun (Pe Raft: $stocFizicScauneMags buc)",
                            idDocStoc = idDocStoc,
                            retetaBOM = retetaBOM // ATRIBUIT CORECT: Pasăm rețeta completă și la nivelul produsului finit
                        )
                    )
                }
            }
        }
        matrixFlux
    }

    val afiseazaPieseLoturi = tabSelectatAtelier0 == "LOTURI" || !ateliereUtilizator.contains("Atelier0")

    val listaPieseFiltrate = remember(pieseTotalizateAfisate, textCautarePiesa, filtruModelSelectat, afiseazaPieseLoturi) {
        if (!afiseazaPieseLoturi) return@remember emptyList<TotalizarePiesaStatus>()

        pieseTotalizateAfisate.filter { p ->
            val sePotrivesteModel = (filtruModelSelectat == "TOATE") || p.modelNume.contains(filtruModelSelectat, ignoreCase = true)
            val sePotrivesteText = textCautarePiesa.isBlank() ||
                    p.denumirePiesa.contains(textCautarePiesa, ignoreCase = true) ||
                    p.reperId.contains(textCautarePiesa, ignoreCase = true)

            sePotrivesteModel && sePotrivesteText
        }
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
                    Text("Raportare Schimb Live", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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

            if (mesajStatus.isNotEmpty()) {
                Text(text = mesajStatus, color = Color(0xFF198754), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            // --- SELECTOR TAB-URI PENTRU ATELIERUL 0: LOTURI vs. SEMIFABRICATE COMUNE ---
            if (ateliereUtilizator.contains("Atelier0")) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { tabSelectatAtelier0 = "LOTURI" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tabSelectatAtelier0 == "LOTURI") MaterialTheme.colorScheme.primary else Color(0xFFE9ECEF),
                            contentColor = if (tabSelectatAtelier0 == "LOTURI") Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("📋 REPERE DIN COMENZI", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Button(
                        onClick = { tabSelectatAtelier0 = "COMUNE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tabSelectatAtelier0 == "COMUNE") Color(0xFFE65100) else Color(0xFFE9ECEF),
                            contentColor = if (tabSelectatAtelier0 == "COMUNE") Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("🪵 REPERE COMUNE", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            // --- FILTRE INTELIGENTE ȘI BARA DE CĂUTARE RAPIDĂ ---
            if (afiseazaPieseLoturi) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Căutare instant după orice literă/cuvânt
                    OutlinedTextField(
                        value = textCautarePiesa,
                        onValueChange = { textCautarePiesa = it },
                        placeholder = { Text("🔍 Caută reperul (ex: picior, traversa,...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (textCautarePiesa.isNotEmpty()) {
                                Text(
                                    text = "✕",
                                    modifier = Modifier.clickable { textCautarePiesa = "" }.padding(8.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // 2. Filtru rapid pe Model / Comandă
                    val modeleDisponibile = remember(pieseTotalizateAfisate) {
                        pieseTotalizateAfisate.map { it.modelNume.split("(").first().trim() }.distinct()
                    }

                    if (modeleDisponibile.size > 1) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = filtruModelSelectat == "TOATE",
                                onClick = { filtruModelSelectat = "TOATE" },
                                label = { Text("TOATE PRODUSELE", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            modeleDisponibile.forEach { modelNumeClean ->
                                FilterChip(
                                    selected = filtruModelSelectat == modelNumeClean,
                                    onClick = { filtruModelSelectat = modelNumeClean },
                                    label = { Text(modelNumeClean.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }

                    if (afiseazaPieseLoturi) {
                        Text("Articole Active în Execuție:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- ZONĂ SCROLLABILĂ ---
        LazyColumn(
            state = listState, // 💡 OBLIGATORIU: Îi dăm listei statul controlabil de scroll
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (tabSelectatAtelier0 == "COMUNE" && ateliereUtilizator.contains("Atelier0")) {
            val catalogPieseComune = run {
                val docComune = listaComenziActive.find { it["modelId"]?.toString() == "semifabricate_comune" }
                val bomComune = (docComune?.get("retetaBOM") as? List<*>) ?: emptyList<Any>()
                bomComune.mapNotNull { it as? Map<*, *> }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFB74D))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🪵 RAPORTARE SEMIFABRICAT COMUN (FAZA 1)", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFFE65100))
                        Text("Alegeți piesa definită în Nomenclator și introduceți cantitatea:", fontSize = 11.sp, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(12.dp))

                        if (catalogPieseComune.isNotEmpty()) {
                            Text("ALEGE PIESA DIN CATALOG:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            Spacer(modifier = Modifier.height(8.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                catalogPieseComune.forEach { pCom ->
                                    val dNume = pCom["denumirePiesa"]?.toString() ?: ""
                                    val fFaza = pCom["fazaPrelucrare"]?.toString() ?: ""
                                    val esteSelectat = (denumireReperGenericText == dNume)
                                    Button(
                                        onClick = {
                                            denumireReperGenericText = dNume
                                            if (fFaza.isNotEmpty()) fazaGenericaText = fFaza
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (esteSelectat) Color(0xFFE65100) else Color.White,
                                            contentColor = if (esteSelectat) Color.White else Color.Black
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(42.dp)
                                    ) {
                                        Text(text = dNume, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            Text("ℹ️ Adăugați piese comune în Nomenclator la modelul '🪵 SEMIFABRICATE COMUNE'", fontSize = 12.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (denumireReperGenericText.isNotEmpty()) {
                            Text("Piesă selectată: $denumireReperGenericText", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFFE65100))
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = cantitateGenericaText,
                            onValueChange = { cantitateGenericaText = it },
                            label = { Text("Cantitate realizată (bucăți)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val qty = cantitateGenericaText.toIntOrNull() ?: 0
                                val denumire = denumireReperGenericText.trim()
                                val faza = fazaGenericaText.trim().ifEmpty { "Prelucrare Faza 1" }
                                if (denumire.isEmpty() || qty <= 0) {
                                    mesajStatus = "Selectați o piesă și introduceți cantitatea!"
                                    return@Button
                                }
                                seIncarca = true
                                val idGeneric = denumire.lowercase().replace(" ", "_").replace("-", "_")
                                val docIdProd = "generic_${usernameCurent}_${System.currentTimeMillis()}"
                                val idDocStocGeneric = "${idGeneric}_Atelier0"

                                val fisaRaport = mapOf(
                                    "articolId" to idGeneric,
                                    "denumirePiesa" to denumire,
                                    "cantitateOk" to qty,
                                    "operator" to usernameCurent,
                                    "atelier" to "Atelier0",
                                    "numarOrdineFaza" to 1,
                                    "numeFaza" to faza,
                                    "esteSemifabricatComun" to true,
                                    "timestamp" to System.currentTimeMillis()
                                )

                                firestore.collection("istoric_productie").document(docIdProd).set(fisaRaport, SetOptions.merge())
                                    .addOnSuccessListener {
                                        firestore.collection("stocuri_faze").document(idDocStocGeneric).get().addOnSuccessListener { dDoc ->
                                            val stocCurent = dDoc.getLong("stocDisponibil") ?: 0
                                            firestore.collection("stocuri_faze").document(idDocStocGeneric)
                                                .set(mapOf("stocDisponibil" to (stocCurent + qty), "faza" to "Atelier 0 - Semifabricate Comune"), SetOptions.merge())
                                        }
                                        seIncarca = false
                                        cantitateGenericaText = ""
                                        mesajStatus = "S-au adăugat $qty buc de '$denumire' în Stocul de Semifabricate!"
                                    }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !seIncarca && denumireReperGenericText.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                        ) {
                            Text("SALVEAZĂ ÎN STOCUL COMUN 📥", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (listaPieseFiltrate.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5))
                ) {
                    Text(
                        text = "Nu s-a găsit niciun reper conform căutării '$textCautarePiesa'.",
                        modifier = Modifier.padding(16.dp),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }

        items(listaPieseFiltrate) { piesa ->
            val cheieIdentificareCard = "${piesa.reperId}_${piesa.atelierAsociat}"
            val esteCardulSelectatAcum = piesaSelectataCheieUnique == cheieIdentificareCard
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(text = piesa.modelNume.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = piesa.denumirePiesa, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF212529))
                            Text(text = "Locație raportare: ${piesa.atelierAsociat}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top=2.dp))
                        }
                        // 💡 SOLUȚIE: Am adăugat .padding(end = 24.dp) pentru a-l trage mai în interiorul cardului
                        // Definim starea locală pentru auto-scale
                        // 1. Definim starea locală pentru auto-scale
                        var dimensiuneFontProcent by remember(piesa.procent) { mutableStateOf(30.sp) }

// 2. Calculăm culoarea solidă în funcție de procentul curent
                        val procentInt = piesa.procent.toInt()
                        val culoareDinamicaSolida = when {
                            procentInt in 0..19   -> Color(0xFF800020) // 🍷 Bordo închis
                            procentInt in 20..49  -> Color(0xFF1976D2) // 🔹 Albastru industrial
                            procentInt in 50..79  -> Color(0xFFF5B041) // 🔸 Galben muștar
                            else                  -> Color(0xFF198754) // ✅ Verde Smarald (100% sau mai mare)
                        }

// 3. Grupăm procentul și pictograma într-o coloană centrată pe partea dreaptă
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(end = 20.dp)
                        ) {
                            Text(
                                text = "$procentInt%",
                                fontSize = dimensiuneFontProcent,
                                fontWeight = FontWeight.Black,
                                color = culoareDinamicaSolida,
                                maxLines = 1,
                                softWrap = false,
                                onTextLayout = { textLayoutResult ->
                                    if (textLayoutResult.didOverflowWidth || textLayoutResult.didOverflowHeight) {
                                        val dimensiuneNoua = (dimensiuneFontProcent.value - 2f)
                                        if (dimensiuneNoua >= 14f) {
                                            dimensiuneFontProcent = dimensiuneNoua.sp
                                        }
                                    }
                                }
                            )

                            // 💡 SOLUȚIE: Adăugăm animația și pictograma de Like doar dacă lotul este finalizat (100%+)
                            AnimatedVisibility(visible = procentInt >= 100) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(modifier = Modifier.height(10.dp)) // Coborât mai jos

                                    Icon(
                                        imageVector = Icons.Outlined.ThumbUp, // 👍 Varianta modernă cu contur, mult mai apropiată de desenul tău
                                        contentDescription = "Lot Finalizat",
                                        tint = Color(0xFF1877F2), // 🔹 Albastrul oficial Facebook
                                        modifier = Modifier.size(28.dp) // Mărit pentru a fi clar și vizibil pe tabletă
                                    )
                                }
                            }
                        }




                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = piesa.procent / 100f,
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        trackColor = Color(0xFFE9ECEF),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("ȚINTĂ PLAN", fontSize = 12.sp, color = Color.Black); Text("${piesa.tintaPlan} buc", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("REALIZAT", fontSize = 12.sp, color = Color.Black); Text("${piesa.realizat} buc", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF198754)) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("RĂMAS", fontSize = 12.sp, color = Color.Black); Text("${piesa.ramas} buc", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFDC3545)) }
                        Button(
                            onClick = {
                                if (esteCardulSelectatAcum && !esteModEditareRaport) {
                                    piesaSelectataCheieUnique = ""
                                } else {
                                    piesaSelectataCheieUnique = cheieIdentificareCard
                                    cantitateNouaText = ""
                                    esteModEditareRaport = false
                                }
                                mesajStatus = ""
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text(if (esteCardulSelectatAcum && !esteModEditareRaport) "Închide" else "Adaugă Producție", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                    AnimatedVisibility(visible = esteCardulSelectatAcum) {
                        Column(modifier = Modifier.padding(top = 16.dp).background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Text(
                                text = if (esteModEditareRaport) "📝 CORECTEAZĂ ACEST RAPORT" else "🏭 RAPORTEAZĂ PRODUCȚIE NOUĂ",
                                fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary
                            )
                            if (piesa.atelierAsociat == "Atelier0") {
                                val fazeDinNomenclator = remember(piesa) {
                                    val listaFaze = mutableListOf<String>()
                                    if (piesa.fazaPrelucrare.isNotBlank()) {
                                        piesa.fazaPrelucrare.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }.forEach { b ->
                                            if (!listaFaze.contains(b)) listaFaze.add(b)
                                        }
                                    }
                                    piesa.retetaBOM.forEach { nod ->
                                        val mapNod = nod as? Map<*, *> ?: emptyMap<Any, Any>()
                                        if (mapNod["atelierAsociat"]?.toString() == "Atelier0") {
                                            val fz = mapNod["fazaPrelucrare"]?.toString() ?: ""
                                            if (fz.isNotBlank()) {
                                                fz.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }.forEach { b ->
                                                    if (!listaFaze.contains(b)) listaFaze.add(b)
                                                }
                                            }
                                        }
                                    }
                                    listaFaze
                                }

                                LaunchedEffect(piesa.reperId, fazeDinNomenclator) {
                                    if (fazeDinNomenclator.isNotEmpty() && !esteModEditareRaport) {
                                        if (!fazeDinNomenclator.contains(numeFazaSelectata)) {
                                            numeFazaSelectata = fazeDinNomenclator.first()
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text("SELECTEAZĂ FAZA DE PRELUCRARE:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (fazeDinNomenclator.isNotEmpty()) {
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        fazeDinNomenclator.forEach { fz ->
                                            val esteSelectat = (numeFazaSelectata == fz)
                                            Button(
                                                onClick = { numeFazaSelectata = fz },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (esteSelectat) Color(0xFF673AB7) else Color(0xFFE9ECEF),
                                                    contentColor = if (esteSelectat) Color.White else Color(0xFF212529)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                modifier = Modifier.height(44.dp)
                                            ) {
                                                Text(
                                                    text = fz,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text("ℹ️ Nicio fază predefinită în Nomenclator BOM pentru această piesă.", fontSize = 11.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = cantitateNouaText,
                                onValueChange = { cantitateNouaText = it },
                                label = { Text("Cantitate OK (bucăți)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { piesaSelectataCheieUnique = ""; cantitateNouaText = ""; esteModEditareRaport = false },
                                    modifier = Modifier.weight(1f)
                                ) { Text("ANULEAZĂ", fontSize = 11.sp) }
                                Button(
                                    onClick = {
                                        val cantitateIntrodusa = cantitateNouaText.toIntOrNull() ?: 0
                                        if (cantitateIntrodusa <= 0) { mesajStatus = "Introduceți o cifră validă!"; return@Button }
                                        seIncarca = true
                                        val calendar = java.util.Calendar.getInstance()
                                        val anCurent = calendar.get(java.util.Calendar.YEAR)
                                        val lunaCurenta = calendar.get(java.util.Calendar.MONTH) + 1
                                        val perioadaLunaStr = String.format("%d-M%02d", anCurent, lunaCurenta)
                                        val docIdProductie = if (esteModEditareRaport) idRaportEditat else "${usernameCurent}_${System.currentTimeMillis()}"
                                        val fisaRaportMap = mutableMapOf<String, Any>(
                                            "articolId" to piesa.reperId,
                                            "denumirePiesa" to piesa.denumirePiesa,
                                            "cantitateOk" to cantitateIntrodusa,
                                            "operator" to usernameCurent,
                                            "atelier" to piesa.atelierAsociat,
                                            "modelNumeScaun" to piesa.modelNume.split("(").first().trim(),
                                            "timestamp" to System.currentTimeMillis(), // 💡 Pune o virgulă aici la sfârșitul rândului existent

                                            // 💡 SOLUȚIE: Adaugă tu manual această linie nouă aici!
                                            "perioada" to perioadaLunaStr
                                        )
                                        if (piesa.atelierAsociat == "Atelier0") {
                                            fisaRaportMap["numarOrdineFaza"] = numarOrdineFaza
                                            fisaRaportMap["numeFaza"] = numeFazaSelectata.ifBlank { "Frezare / Profilare" }
                                        }
                                        firestore.collection("istoric_productie").document(docIdProductie).set(fisaRaportMap, SetOptions.merge())
                                            .addOnSuccessListener {
                                                val diferentaDepozit = if (esteModEditareRaport) (cantitateIntrodusa - cantitateRaportataAnterior) else cantitateIntrodusa
// Actualizăm raftul magazinajului curent
                                                // Căutăm documentul de stoc cu sufixul atelierului, dacă nu există, încercăm fără sufix
                                                val idDocStocCuSufix = piesa.idDocStoc
                                                firestore.collection("stocuri_faze").document(idDocStocCuSufix).get().addOnSuccessListener { dDoc ->
                                                    if (dDoc.exists()) {
                                                        val stocCurentFizic = dDoc.getLong("stocDisponibil") ?: 0
                                                        firestore.collection("stocuri_faze").document(idDocStocCuSufix)
                                                            .set(mapOf("stocDisponibil" to (stocCurentFizic + diferentaDepozit)), SetOptions.merge())
                                                    } else {
                                                        // Încercăm fără sufixul atelierului
                                                        val idDocStocFaraSufix = piesa.reperId
                                                        firestore.collection("stocuri_faze").document(idDocStocFaraSufix).get().addOnSuccessListener { dDoc2 ->
                                                            val stocCurentFizic = dDoc2.getLong("stocDisponibil") ?: 0
                                                            firestore.collection("stocuri_faze").document(idDocStocFaraSufix)
                                                                .set(mapOf("stocDisponibil" to (stocCurentFizic + diferentaDepozit)), SetOptions.merge())
                                                        }
                                                    }
                                                }
// --- OBLIGATORIU: CONSUM RETETĂ BOM EXTINS (VALABIL ȘI PENTRU AMBALARE FINALĂ ATELIER 4) ---
                                                // --- CONSUM RETETĂ BOM EXTINS ȘI IERARHIC (CORECTAT PENTRU ATELIERUL 4) ---
                                                val diferentaConsumBOM = if (esteModEditareRaport) (cantitateIntrodusa - cantitateRaportataAnterior) else cantitateIntrodusa
                                                if (diferentaConsumBOM != 0) {
                                                    piesa.retetaBOM.forEach { nodBom ->
                                                        val compBom = nodBom as? Map<*, *> ?: emptyMap<Any, Any>()
                                                        val atelierAsociatReperSub = compBom["atelierAsociat"]?.toString() ?: ""
                                                        val destinatieSubansamblu = compBom["subansambluDestinatie"]?.toString() ?: ""
                                                        val rIdSub = compBom["reperId"]?.toString() ?: ""
                                                        val listaSubansamble = (compBom["subansambleList"] as? List<*>)?.map { it.toString().trim() } ?: emptyList()

                                                        // Verificăm dacă piesa din BOM este destinată subansamblului/agregatului curent raportat în Atelier 2
                                                        val destinatiiPiesaList = if (listaSubansamble.isNotEmpty()) {
                                                            listaSubansamble
                                                        } else {
                                                            destinatieSubansamblu.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                                        }

                                                        val esteDestinataAgregatuluiCurent = (piesa.atelierAsociat == "Atelier2") && (
                                                            destinatiiPiesaList.any { it.equals(piesa.reperId, ignoreCase = true) || it.equals(piesa.denumirePiesa, ignoreCase = true) } ||
                                                            destinatieSubansamblu.equals(piesa.reperId, ignoreCase = true) ||
                                                            destinatieSubansamblu.contains(piesa.reperId, ignoreCase = true)
                                                        )

                                                        // REGULĂ DE SCĂDERE DIN STOCUL FAZELOR ANTERIOARE ÎN FUNCȚIE DE NOMENCLATOR:
                                                        // 0. Dacă piesa provine din Atelierul 0:
                                                        //    - se scade când este raportată în atelierul specificat ca destinație (ex: subansambluDestinatie = "atelier 1" / "Atelier1")
                                                        //    - sau când piesa din atelierul următor are același reperId (flux consecutiv pe același reper: At0 -> At1)
                                                        //    - sau dacă destinația din At0 indică reperul curent
                                                        val estePiesaDinAt0PentruCurent = (atelierAsociatReperSub == "Atelier0") && (
                                                            destinatieSubansamblu.equals(piesa.reperId, ignoreCase = true) ||
                                                            destinatieSubansamblu.replace(" ", "").equals(piesa.atelierAsociat, ignoreCase = true) ||
                                                            (rIdSub == piesa.reperId && piesa.atelierAsociat != "Atelier0")
                                                        )

                                                        val trebuieScazutAcum = if (estePiesaDinAt0PentruCurent) {
                                                            true
                                                        } else if (piesa.atelierAsociat == "Atelier1") {
                                                            atelierAsociatReperSub == "Atelier0" && (destinatieSubansamblu.isEmpty() || destinatieSubansamblu.replace(" ", "").contains("atelier1", ignoreCase = true))
                                                        } else if (piesa.atelierAsociat == "Atelier2") {
                                                            esteDestinataAgregatuluiCurent
                                                        } else if (piesa.atelierAsociat == "Atelier4") {
                                                            // La ambalarea finală se consumă:
                                                            // - agregatele din Atelierul 2
                                                            // - cutiile/șezuturile din Atelierul 4 (fără destinație)
                                                            // - piesele din Atelierul 1 care merg direct în ambalaj (fără subansamblu intermediar)
                                                            atelierAsociatReperSub == "Atelier2" ||
                                                            (atelierAsociatReperSub == "Atelier4" && destinatieSubansamblu.isEmpty()) ||
                                                            (atelierAsociatReperSub == "Atelier1" && destinatieSubansamblu.isEmpty() && listaSubansamble.isEmpty())
                                                        } else {
                                                            false
                                                        }

                                                        if (trebuieScazutAcum) {
                                                            val rIdSub = compBom["reperId"]?.toString() ?: ""
                                                            // ATENȚIE RATĂ DE CONSUM:
                                                            // 1. Dacă raportăm o piesă intermediară (Atelier 1, etc.), cantitatea raportată este chiar numărul de PIESE prelucrate (1 la 1 cu At0).
                                                            // 2. Dacă raportăm în Atelier 2 (subansamblu/agregat):
                                                            //    - Verificăm dacă există o cantitate specifică definită pentru acest subansamblu în "cantitatiSubansamble".
                                                            //    - Dacă nu există, dar piesa este partajată între mai multe subansamble (destinatiiPiesaList.size > 1),
                                                            //      împărțim cantitatea totală per scaun la numărul de subansamble (ex: 2 traverse / 2 agregate = 1 buc/agregat).
                                                            //    - Altfel, folosim cantitateNecesara per scaun.
                                                            val cantitateNecesaraTotala = (compBom["cantitateNecesara"] as? Long)?.toInt() ?: 1
                                                            val cantitatiSubansambleMap = (compBom["cantitatiSubansamble"] as? Map<*, *>) ?: emptyMap<Any, Any>()

                                                            val deConsumat = if (piesa.atelierAsociat == "Atelier1" || atelierAsociatReperSub == "Atelier0") {
                                                                1
                                                            } else if (piesa.atelierAsociat == "Atelier2") {
                                                                // Căutăm cheia corespunzătoare subansamblului curent în map-ul salvat din Nomenclator
                                                                val gasitInMap = cantitatiSubansambleMap.entries.firstOrNull { (k, _) ->
                                                                    val kStr = k?.toString()?.trim()?.lowercase() ?: ""
                                                                    kStr.isNotEmpty() && (
                                                                        kStr == piesa.reperId.trim().lowercase() ||
                                                                        kStr == piesa.denumirePiesa.trim().lowercase() ||
                                                                        piesa.reperId.trim().lowercase().contains(kStr) ||
                                                                        piesa.denumirePiesa.trim().lowercase().contains(kStr)
                                                                    )
                                                                }?.value

                                                                val cantitateConfigurata = (gasitInMap as? Long)?.toInt() ?: (gasitInMap as? Int)
                                                                if (cantitateConfigurata != null && cantitateConfigurata > 0) {
                                                                    cantitateConfigurata
                                                                } else if (destinatiiPiesaList.size > 1) {
                                                                    // Fallback inteligent: dacă sunt N subansamble destinație, împărțim în mod egal
                                                                    val impartit = cantitateNecesaraTotala / destinatiiPiesaList.size
                                                                    if (impartit > 0) impartit else 1
                                                                } else {
                                                                    cantitateNecesaraTotala
                                                                }
                                                            } else {
                                                                cantitateNecesaraTotala
                                                            }

                                                            val totalModificare = diferentaConsumBOM * deConsumat
                                                            val idDocSubFazaCuSufix = "${rIdSub}_$atelierAsociatReperSub"

                                                            // Căutăm documentul de stoc cu sufixul atelierului, dacă nu există, încercăm fără sufix
                                                            firestore.collection("stocuri_faze").document(idDocSubFazaCuSufix).get().addOnSuccessListener { dSub ->
                                                                if (dSub.exists()) {
                                                                    val stocSubCurent = dSub.getLong("stocDisponibil") ?: 0
                                                                    firestore.collection("stocuri_faze").document(idDocSubFazaCuSufix)
                                                                        .set(mapOf("stocDisponibil" to (stocSubCurent - totalModificare)), SetOptions.merge())
                                                                } else {
                                                                    // Încercăm fără sufixul atelierului
                                                                    val idDocSubFazaFaraSufix = rIdSub
                                                                    firestore.collection("stocuri_faze").document(idDocSubFazaFaraSufix).get().addOnSuccessListener { dSub2 ->
                                                                        val stocSubCurent = dSub2.getLong("stocDisponibil") ?: 0
                                                                        firestore.collection("stocuri_faze").document(idDocSubFazaFaraSufix)
                                                                            .set(mapOf("stocDisponibil" to (stocSubCurent - totalModificare)), SetOptions.merge())
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

// --- CONSUM AUTOMAT CONSUMABILE & MATERIALE AUXILIARE (Aracet, Lac, Feronerie etc.) ---
                                                // 1. Căutăm reperul curent în BOM-ul comenzii pentru a-i găsi consumabileList asociat
                                                val compCurentInBom = piesa.retetaBOM.find { nod ->
                                                    val map = nod as? Map<*, *> ?: return@find false
                                                    map["reperId"]?.toString() == piesa.reperId && map["atelierAsociat"]?.toString() == piesa.atelierAsociat
                                                } as? Map<*, *> ?: piesa.retetaBOM.find { (it as? Map<*, *>)?.get("reperId")?.toString() == piesa.reperId } as? Map<*, *>

                                                val listaConsumabileAsociate = (compCurentInBom?.get("consumabileList") as? List<*>) ?: emptyList<Any>()

                                                if (diferentaConsumBOM != 0 && listaConsumabileAsociate.isNotEmpty()) {
                                                    val saptamanaCurenta = calendar.get(java.util.Calendar.WEEK_OF_YEAR)
                                                    val cheieSapt = String.format(java.util.Locale.getDefault(), "%d-S%02d", anCurent, saptamanaCurenta)
                                                    val cheieLuna = String.format(java.util.Locale.getDefault(), "%d-M%02d", anCurent, lunaCurenta)

                                                    listaConsumabileAsociate.forEach { cNode ->
                                                        val cMap = cNode as? Map<*, *> ?: return@forEach
                                                        val cNume = cMap["numeConsumabil"]?.toString()?.trim() ?: return@forEach
                                                        val cNorma = (cMap["normaPerBuc"] as? Number)?.toDouble() ?: 0.0
                                                        val cUM = cMap["unitate"]?.toString() ?: "buc"
                                                        if (cNorma > 0.0) {
                                                            val cantitateConsumataTotala = diferentaConsumBOM * cNorma
                                                            val idDocConsumabil = cNume.lowercase().replace(" ", "_")

                                                            // 1. Scădem din stocul live al consumabilului
                                                            val docRefStoc = firestore.collection("stocuri_consumabile").document(idDocConsumabil)
                                                            docRefStoc.get().addOnSuccessListener { dCons ->
                                                                val stocCurent = (dCons.get("stocDisponibil") as? Number)?.toDouble() ?: 0.0
                                                                val stocNou = (stocCurent - cantitateConsumataTotala).coerceAtLeast(0.0)
                                                                docRefStoc.set(
                                                                    mapOf(
                                                                        "numeConsumabil" to cNume,
                                                                        "unitate" to cUM,
                                                                        "stocDisponibil" to stocNou,
                                                                        "ultimulUpdate" to System.currentTimeMillis()
                                                                    ),
                                                                    SetOptions.merge()
                                                                )
                                                            }

                                                            // 2. Înregistrăm în istoricul de consumabile pentru rapoarte săptămânale și lunare
                                                            val istoricConsumMap = mapOf(
                                                                "tipTranzactie" to "consum",
                                                                "numeConsumabil" to cNume,
                                                                "cantitate" to cantitateConsumataTotala,
                                                                "unitate" to cUM,
                                                                "operator" to usernameCurent,
                                                                "reperSauModel" to piesa.denumirePiesa,
                                                                "articolId" to piesa.reperId,
                                                                "cantitatePieseProduse" to diferentaConsumBOM,
                                                                "atelier" to piesa.atelierAsociat,
                                                                "timestamp" to System.currentTimeMillis(),
                                                                "saptamana" to cheieSapt,
                                                                "luna" to cheieLuna
                                                            )
                                                            firestore.collection("istoric_consumabile").add(istoricConsumMap)
                                                        }
                                                    }
                                                }

                                                seIncarca = false; piesaSelectataCheieUnique = ""; cantitateNouaText = ""
                                                esteModEditareRaport = false
                                                mesajStatus = "Salvare efectuată live în Cloud!"
                                            }
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    enabled = !seIncarca
                                ) { Text("CONFIRMĂ", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Istoricul Raportărilor tale:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ASTAZI" to "Azi",
                    "ULTIMELE_3_ZILE" to "3 Zile",
                    "SAPTAMANA" to "7 Zile",
                    "TOATE" to "Toate"
                ).forEach { (cod, label) ->
                    FilterChip(
                        selected = filtruIstoricPerioada == cod,
                        onClick = { filtruIstoricPerioada = cod },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
        val msPerZi = 24L * 3600L * 1000L
        val acumMs = System.currentTimeMillis()
        val calendarInceputAzi = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val fiseOperatorLogat = istoricRaportariPersonale.filter { fisa ->
            val matchOp = fisa["operator"]?.toString() == usernameCurent
            if (!matchOp) return@filter false
            val ts = (fisa["timestamp"] as? Long) ?: 0L
            when (filtruIstoricPerioada) {
                "ASTAZI" -> ts >= calendarInceputAzi
                "ULTIMELE_3_ZILE" -> ts >= (acumMs - 3L * msPerZi)
                "SAPTAMANA" -> ts >= (acumMs - 7L * msPerZi)
                else -> true
            }
        }
        if (fiseOperatorLogat.isEmpty()) {
            item { Text("Nu există raportări în perioada selectată.", fontSize = 13.sp, color = Color.Gray) }
        }
        items(fiseOperatorLogat) { fisa ->
            val fIdDoc = fisa["idDoc"]?.toString() ?: ""
            val fArticolId = fisa["articolId"]?.toString() ?: ""
            val fDenumire = fisa["denumirePiesa"]?.toString() ?: fArticolId
            val fQty = (fisa["cantitateOk"] as? Long)?.toInt() ?: 0
            val fAtelier = fisa["atelier"]?.toString() ?: "Atelier1"
            val fNumarFaza = (fisa["numarOrdineFaza"] as? Long)?.toInt() ?: 0
            val fNumeFaza = fisa["numeFaza"]?.toString() ?: ""
            val textFazaDetalii = if (fAtelier == "Atelier0" && fNumarFaza > 0) "⚙️ Faza $fNumarFaza ($fNumeFaza)" else "Faza: $fAtelier"
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val fModelScaun = fisa["modelNumeScaun"]?.toString() ?: "Scaun"

                        Text(
                            text = "${fModelScaun.uppercase()}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                        Text(text = fDenumire, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "$textFazaDetalii | Cantitate: $fQty buc", fontSize = 12.sp, color = Color.DarkGray)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val fazaGasitaMatrice = pieseTotalizateAfisate.find { it.reperId == fArticolId && it.atelierAsociat == fAtelier }
                                if (fazaGasitaMatrice != null) {
                                    piesaSelectataCheieUnique = "${fArticolId}_$fAtelier"
                                    cantitateNouaText = fQty.toString()
                                    if (fAtelier == "Atelier0") {
                                        numarOrdineFaza = if (fNumarFaza > 0) fNumarFaza else 1
                                        numeFazaSelectata = if (fNumeFaza.isNotEmpty()) fNumeFaza else "Frezare / Profilare"
                                    }
                                    esteModEditareRaport = true
                                    idRaportEditat = fIdDoc
                                    cantitateRaportataAnterior = fQty
                                    mesajStatus = ""

                                    // 💡 ANIMĂM SCROLL-UL CORECT CĂTRE CARDUL PIESEI
                                    // Căutăm poziția (indexul) piesei în lista curentă filtrată de pe ecran
                                    val indexPiesaInLista = listaPieseFiltrate.indexOfFirst {
                                        it.reperId == fArticolId && it.atelierAsociat == fAtelier
                                    }

                                    if (indexPiesaInLista != -1) {
                                        // 💡 SOLUȚIE: Adăugat punctul salvator "." înainte de launch
                                        coroutineScope.launch {
                                            val indexDestinatieUil = if (tabSelectatAtelier0 == "COMUNE") indexPiesaInLista + 2 else indexPiesaInLista + 1

                                            // 💡 SOLUȚIE: scrollOffset trimite cardul mai jos cu un număr fix de pixeli (ex: -150 pixeli)
                                            // Valoarea negativă împinge elementul în jos pe ecran, lăsându-l complet vizibil
                                            listState.animateScrollToItem(
                                                index = indexDestinatieUil.coerceAtLeast(0),
                                                scrollOffset = -1200
                                            )
                                        }

                                    }

                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Corectează ✏️", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                        Button(
                            onClick = {
                                seIncarca = true
                                val idDocStocOriginal = "${fArticolId}_$fAtelier"
                                firestore.collection("stocuri_faze").document(idDocStocOriginal).get().addOnSuccessListener { dDoc ->
                                    val stocCurent = dDoc.getLong("stocDisponibil") ?: 0
                                    firestore.collection("stocuri_faze").document(idDocStocOriginal)
                                        .set(mapOf("stocDisponibil" to (stocCurent - fQty)), SetOptions.merge())
                                        .addOnSuccessListener {
                                            // RESTITUIRE AUTOMATĂ ÎN STOCUL PIESELOR CONSUMATE (REVERSIE CONSUM BOM)
                                            val fazaGasitaMatrice = pieseTotalizateAfisate.find { it.reperId == fArticolId && it.atelierAsociat == fAtelier }
                                            if (fazaGasitaMatrice != null) {
                                                fazaGasitaMatrice.retetaBOM.forEach { nodBom ->
                                                    val compBom = nodBom as? Map<*, *> ?: emptyMap<Any, Any>()
                                                    val atelierAsociatReperSub = compBom["atelierAsociat"]?.toString() ?: ""
                                                    val destinatieSubansamblu = compBom["subansambluDestinatie"]?.toString() ?: ""
                                                    val rIdSub = compBom["reperId"]?.toString() ?: ""
                                                    val listaSubansamble = (compBom["subansambleList"] as? List<*>)?.map { it.toString().trim() } ?: emptyList()

                                                    val destinatiiPiesaList = if (listaSubansamble.isNotEmpty()) {
                                                        listaSubansamble
                                                    } else {
                                                        destinatieSubansamblu.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                                    }

                                                    val esteDestinataAgregatuluiCurent = (fAtelier == "Atelier2") && (
                                                        destinatiiPiesaList.any { it.equals(fArticolId, ignoreCase = true) || it.equals(fazaGasitaMatrice.denumirePiesa, ignoreCase = true) } ||
                                                        destinatieSubansamblu.equals(fArticolId, ignoreCase = true) ||
                                                        destinatieSubansamblu.contains(fArticolId, ignoreCase = true)
                                                    )

                                                    val estePiesaDinAt0PentruCurent = (atelierAsociatReperSub == "Atelier0") && (
                                                        destinatieSubansamblu.equals(fArticolId, ignoreCase = true) ||
                                                        destinatieSubansamblu.replace(" ", "").equals(fAtelier, ignoreCase = true) ||
                                                        (rIdSub == fArticolId && fAtelier != "Atelier0")
                                                    )

                                                    val aFostScazut = when (fAtelier) {
                                                        "Atelier1" -> estePiesaDinAt0PentruCurent || (atelierAsociatReperSub == "Atelier0" && (destinatieSubansamblu.isEmpty() || destinatieSubansamblu.replace(" ", "").contains("atelier1", ignoreCase = true)))
                                                        "Atelier2" -> estePiesaDinAt0PentruCurent || esteDestinataAgregatuluiCurent
                                                        "Atelier3" -> estePiesaDinAt0PentruCurent
                                                        "Atelier4" -> {
                                                            val destinațieCurată = destinatieSubansamblu.trim()
                                                            estePiesaDinAt0PentruCurent ||
                                                            atelierAsociatReperSub == "Atelier2" ||
                                                                    (atelierAsociatReperSub == "Atelier4" && destinațieCurată.isEmpty()) ||
                                                                    (atelierAsociatReperSub == "Atelier1" && destinațieCurată.isEmpty() && listaSubansamble.isEmpty())
                                                        }
                                                        else -> false
                                                    }

                                                    if (aFostScazut) {
                                                        val rIdSub = compBom["reperId"]?.toString() ?: ""
                                                        val cantitateNecesaraTotala = (compBom["cantitateNecesara"] as? Long)?.toInt() ?: 1
                                                        val cantitatiSubansambleMap = (compBom["cantitatiSubansamble"] as? Map<*, *>) ?: emptyMap<Any, Any>()

                                                        val deConsumat = if (fAtelier == "Atelier1" || atelierAsociatReperSub == "Atelier0") {
                                                            1
                                                        } else if (fAtelier == "Atelier2") {
                                                            val gasitInMap = cantitatiSubansambleMap.entries.firstOrNull { (k, _) ->
                                                                val kStr = k?.toString()?.trim()?.lowercase() ?: ""
                                                                kStr.isNotEmpty() && (
                                                                    kStr == fArticolId.trim().lowercase() ||
                                                                    kStr == fazaGasitaMatrice.denumirePiesa.trim().lowercase() ||
                                                                    fArticolId.trim().lowercase().contains(kStr) ||
                                                                    fazaGasitaMatrice.denumirePiesa.trim().lowercase().contains(kStr)
                                                                )
                                                            }?.value

                                                            val cantitateConfigurata = (gasitInMap as? Long)?.toInt() ?: (gasitInMap as? Int)
                                                            if (cantitateConfigurata != null && cantitateConfigurata > 0) {
                                                                cantitateConfigurata
                                                            } else if (destinatiiPiesaList.size > 1) {
                                                                val impartit = cantitateNecesaraTotala / destinatiiPiesaList.size
                                                                if (impartit > 0) impartit else 1
                                                            } else {
                                                                cantitateNecesaraTotala
                                                            }
                                                        } else {
                                                            cantitateNecesaraTotala
                                                        }

                                                        val totalRestituire = fQty * deConsumat
                                                        val idDocSubFazaCuSufix = "${rIdSub}_$atelierAsociatReperSub"

                                                        // Căutăm documentul de stoc cu sufixul atelierului, dacă nu există, încercăm fără sufix
                                                        firestore.collection("stocuri_faze").document(idDocSubFazaCuSufix).get().addOnSuccessListener { dSub ->
                                                            if (dSub.exists()) {
                                                                val stocSubCurent = dSub.getLong("stocDisponibil") ?: 0
                                                                firestore.collection("stocuri_faze").document(idDocSubFazaCuSufix)
                                                                    .set(mapOf("stocDisponibil" to (stocSubCurent + totalRestituire)), SetOptions.merge())
                                                            } else {
                                                                // Încercăm fără sufixul atelierului
                                                                val idDocSubFazaFaraSufix = rIdSub
                                                                firestore.collection("stocuri_faze").document(idDocSubFazaFaraSufix).get().addOnSuccessListener { dSub2 ->
                                                                    val stocSubCurent = dSub2.getLong("stocDisponibil") ?: 0
                                                                    firestore.collection("stocuri_faze").document(idDocSubFazaFaraSufix)
                                                                        .set(mapOf("stocDisponibil" to (stocSubCurent + totalRestituire)), SetOptions.merge())
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // RESTITUIRE AUTOMATĂ CONSUMABILE & MATERIALE AUXILIARE
                                                val compCurentInBom = fazaGasitaMatrice.retetaBOM.find { nod ->
                                                    val map = nod as? Map<*, *> ?: return@find false
                                                    map["reperId"]?.toString() == fArticolId && map["atelierAsociat"]?.toString() == fAtelier
                                                } as? Map<*, *> ?: fazaGasitaMatrice.retetaBOM.find { (it as? Map<*, *>)?.get("reperId")?.toString() == fArticolId } as? Map<*, *>

                                                val listaConsumabileAsociate = (compCurentInBom?.get("consumabileList") as? List<*>) ?: emptyList<Any>()
                                                listaConsumabileAsociate.forEach { cNode ->
                                                    val cMap = cNode as? Map<*, *> ?: return@forEach
                                                    val cNume = cMap["numeConsumabil"]?.toString()?.trim() ?: return@forEach
                                                    val cNorma = (cMap["normaPerBuc"] as? Number)?.toDouble() ?: 0.0
                                                    val cUM = cMap["unitate"]?.toString() ?: "buc"
                                                    if (cNorma > 0.0) {
                                                        val deRestituit = fQty * cNorma
                                                        val idDocConsumabil = cNume.lowercase().replace(" ", "_")
                                                        val docRefStoc = firestore.collection("stocuri_consumabile").document(idDocConsumabil)
                                                        docRefStoc.get().addOnSuccessListener { dCons ->
                                                            val stocCurent = (dCons.get("stocDisponibil") as? Number)?.toDouble() ?: 0.0
                                                            docRefStoc.set(
                                                                mapOf(
                                                                    "numeConsumabil" to cNume,
                                                                    "unitate" to cUM,
                                                                    "stocDisponibil" to (stocCurent + deRestituit),
                                                                    "ultimulUpdate" to System.currentTimeMillis()
                                                                ),
                                                                SetOptions.merge()
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            firestore.collection("istoric_productie").document(fIdDoc).delete()
                                                .addOnSuccessListener {
                                                    seIncarca = false
                                                    mesajStatus = "Raportarea a fost ștearsă iar stocurile consumate au fost RESTITUITE!"
                                                }
                                        }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            enabled = !seIncarca
                        ) { Text("Șterge 🗑️", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    }
                }
            }
        }
        }
    }
