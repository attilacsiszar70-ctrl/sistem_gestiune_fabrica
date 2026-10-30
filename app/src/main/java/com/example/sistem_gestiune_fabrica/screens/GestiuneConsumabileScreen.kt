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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Tune
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestiuneConsumabileScreen(
    firestore: FirebaseFirestore,
    userData: Map<String, Any>,
    onBackClick: () -> Unit
) {
    val usernameCurent = userData["username"]?.toString() ?: "Utilizator"

    // Stări
    var tabSelectat by remember { mutableStateOf("STOCURI") } // "STOCURI", "RAPORT_SAPTAMANA", "RAPORT_LUNA", "ISTORIC_INTRARI"
    var listaStocuriConsumabile by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var istoricConsum by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaComenzi by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var istoricProductie by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var seIncarca by remember { mutableStateOf(true) }
    var statusMesaj by remember { mutableStateOf("") }

    // Dialog / Formular Intrare Stoc Nou
    var afiseazaDialogIntrare by remember { mutableStateOf(false) }
    var numeConsumabilIntrare by remember { mutableStateOf("") }
    var cantitateIntrareText by remember { mutableStateOf("") }
    var unitateMasuraIntrare by remember { mutableStateOf("kg") }
    var observatiiIntrare by remember { mutableStateOf("") }

    // Dialog Corectare / Ajustare Stoc Fizic Direct
    var afiseazaDialogAjustareStoc by remember { mutableStateOf(false) }
    var consumabilAjustatId by remember { mutableStateOf("") }
    var consumabilAjustatNume by remember { mutableStateOf("") }
    var stocNouCorectatText by remember { mutableStateOf("") }
    var umConsumabilAjustat by remember { mutableStateOf("kg") }

    // Filtru consumabil specific în rapoarte
    var filtruConsumabilSelectat by remember { mutableStateOf("TOATE") }

    LaunchedEffect(Unit) {
        firestore.collection("stocuri_consumabile").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaStocuriConsumabile = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("istoric_consumabile").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                istoricConsum = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaComenzi = snapshot.documents.map { it.data ?: emptyMap() }
            }
        }
        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                istoricProductie = snapshot.documents.map { it.data ?: emptyMap() }
                seIncarca = false
            }
        }
    }

    // Funcție pentru adăugare / aprovizionare stoc consumabil
    fun adaugaIntrareStoc() {
        val nume = numeConsumabilIntrare.trim()
        val cant = cantitateIntrareText.toDoubleOrNull() ?: 0.0
        if (nume.isEmpty() || cant <= 0.0) {
            statusMesaj = "Completați un nume și o cantitate validă!"
            return
        }
        val idDoc = nume.lowercase().replace(" ", "_")
        seIncarca = true

        val calendar = Calendar.getInstance()
        val anCurent = calendar.get(Calendar.YEAR)
        val saptamanaCurenta = calendar.get(Calendar.WEEK_OF_YEAR)
        val lunaCurenta = calendar.get(Calendar.MONTH) + 1
        val cheieSapt = String.format(Locale.getDefault(), "%d-S%02d", anCurent, saptamanaCurenta)
        val cheieLuna = String.format(Locale.getDefault(), "%d-M%02d", anCurent, lunaCurenta)

        // 1. Înregistrare în istoric_consumabile ca "intrare"
        val intrareMap = mapOf(
            "tipTranzactie" to "intrare",
            "numeConsumabil" to nume,
            "cantitate" to cant,
            "unitate" to unitateMasuraIntrare,
            "operator" to usernameCurent,
            "observatii" to observatiiIntrare.trim(),
            "timestamp" to System.currentTimeMillis(),
            "saptamana" to cheieSapt,
            "luna" to cheieLuna
        )
        firestore.collection("istoric_consumabile").add(intrareMap)

        // 2. Actualizare stoc curent
        val docRef = firestore.collection("stocuri_consumabile").document(idDoc)
        docRef.get().addOnSuccessListener { doc ->
            val stocVechi = (doc.get("stocDisponibil") as? Number)?.toDouble() ?: 0.0
            val stocNou = stocVechi + cant
            docRef.set(
                mapOf(
                    "numeConsumabil" to nume,
                    "unitate" to unitateMasuraIntrare,
                    "stocDisponibil" to stocNou,
                    "ultimulUpdate" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                seIncarca = false
                afiseazaDialogIntrare = false
                numeConsumabilIntrare = ""
                cantitateIntrareText = ""
                observatiiIntrare = ""
                statusMesaj = "Stoc adăugat cu succes: +$cant $unitateMasuraIntrare la '$nume'!"
            }
        }.addOnFailureListener {
            seIncarca = false
            statusMesaj = "Eroare salvare stoc."
        }
    }

    // Funcție pentru corectare / reglare directă a stocului faptic
    fun salveazaAjustareStoc() {
        val stocNou = stocNouCorectatText.toDoubleOrNull()
        if (consumabilAjustatId.isEmpty() || stocNou == null || stocNou < 0.0) {
            statusMesaj = "Introduceți o cantitate validă (pozitivă sau zero)!"
            return
        }
        seIncarca = true
        val docRef = firestore.collection("stocuri_consumabile").document(consumabilAjustatId)
        docRef.get().addOnSuccessListener { dDoc ->
            val stocVechi = (dDoc.get("stocDisponibil") as? Number)?.toDouble() ?: 0.0
            val diferenta = stocNou - stocVechi

            docRef.set(
                mapOf(
                    "stocDisponibil" to stocNou,
                    "ultimulUpdate" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                // Înregistrăm ajustarea în istoric pentru trasabilitate
                val calendar = Calendar.getInstance()
                val anCurent = calendar.get(Calendar.YEAR)
                val saptamanaCurenta = calendar.get(Calendar.WEEK_OF_YEAR)
                val lunaCurenta = calendar.get(Calendar.MONTH) + 1
                val cheieSapt = String.format(Locale.getDefault(), "%d-S%02d", anCurent, saptamanaCurenta)
                val cheieLuna = String.format(Locale.getDefault(), "%d-M%02d", anCurent, lunaCurenta)

                val ajustareMap = mapOf(
                    "tipTranzactie" to "ajustare_inventar",
                    "numeConsumabil" to consumabilAjustatNume,
                    "cantitate" to diferenta,
                    "stocVechi" to stocVechi,
                    "stocNou" to stocNou,
                    "unitate" to umConsumabilAjustat,
                    "operator" to usernameCurent,
                    "observatii" to "Corectare/Reglare stoc recepție de către $usernameCurent",
                    "timestamp" to System.currentTimeMillis(),
                    "saptamana" to cheieSapt,
                    "luna" to cheieLuna
                )
                firestore.collection("istoric_consumabile").add(ajustareMap)

                seIncarca = false
                afiseazaDialogAjustareStoc = false
                statusMesaj = "Stocul pentru '$consumabilAjustatNume' a fost corectat cu succes la $stocNou $umConsumabilAjustat!"
            }
        }.addOnFailureListener {
            seIncarca = false
            statusMesaj = "Eroare la corectarea stocului."
        }
    }

    // Funcție pentru anularea / ștergerea unei note de intrare greșite din istoric
    fun anuleazaNotaIntrare(idDocIstoric: String, numeConsumabil: String, cantitateIntrata: Double) {
        seIncarca = true
        val idDocStoc = numeConsumabil.lowercase().replace(" ", "_")
        val docRefStoc = firestore.collection("stocuri_consumabile").document(idDocStoc)

        docRefStoc.get().addOnSuccessListener { dDoc ->
            val stocCurent = (dDoc.get("stocDisponibil") as? Number)?.toDouble() ?: 0.0
            val stocRecalculat = (stocCurent - cantitateIntrata).coerceAtLeast(0.0)

            docRefStoc.set(
                mapOf(
                    "stocDisponibil" to stocRecalculat,
                    "ultimulUpdate" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                firestore.collection("istoric_consumabile").document(idDocIstoric).delete().addOnSuccessListener {
                    seIncarca = false
                    statusMesaj = "Nota de intrare a fost ștearsă iar cantitatea de $cantitateIntrata a fost scăzută din stoc!"
                }
            }
        }.addOnFailureListener {
            seIncarca = false
            statusMesaj = "Eroare la anularea notei de intrare."
        }
    }

    // --- CALCUL CONSOLIDAT & RETROACTIV DIN TOATĂ PRODUCȚIA ---
    // Extragem harta consumabilelor definite pe fiecare piesă/scaun din Nomenclator
    // Cheie: "reperId|atelier" -> Lista de consumabile cu norma lor
    val hartaConsumabileNomenclator = remember(listaComenzi) {
        val map = mutableMapOf<String, List<Map<String, Any>>>()
        for (cmd in listaComenzi) {
            val reteta = (cmd["retetaBOM"] as? List<*>) ?: emptyList<Any>()
            for (nod in reteta) {
                val p = nod as? Map<*, *> ?: continue
                val rId = p["reperId"]?.toString()?.trim()?.lowercase() ?: ""
                val dNume = p["denumirePiesa"]?.toString()?.trim()?.lowercase() ?: ""
                val at = p["atelierAsociat"]?.toString()?.trim()?.lowercase() ?: ""
                val cList = (p["consumabileList"] as? List<*>)?.mapNotNull { it as? Map<String, Any> } ?: emptyList()
                if (cList.isNotEmpty()) {
                    if (rId.isNotEmpty()) {
                        map["$rId|$at"] = cList
                        map[rId] = cList
                    }
                    if (dNume.isNotEmpty()) {
                        map["$dNume|$at"] = cList
                        map[dNume] = cList
                    }
                }
            }
        }
        map
    }

    // Calculăm consumurile retroactiv din tot istoricul producției
    val consumuriRetroactiveDinProductie = remember(istoricProductie, hartaConsumabileNomenclator) {
        val listaRezultate = mutableListOf<Map<String, Any>>()
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }

        for (prod in istoricProductie) {
            val rId = prod["articolId"]?.toString()?.trim()?.lowercase() ?: ""
            val dNume = prod["denumirePiesa"]?.toString()?.trim()?.lowercase() ?: ""
            val at = prod["atelier"]?.toString()?.trim()?.lowercase() ?: ""
            val cantitateOk = (prod["cantitateOk"] as? Number)?.toDouble() ?: 0.0
            if (cantitateOk <= 0.0) continue

            // Găsim consumabilele configurate pentru această piesă
            val consumabileAsociate = hartaConsumabileNomenclator["$rId|$at"]
                ?: hartaConsumabileNomenclator[rId]
                ?: hartaConsumabileNomenclator["$dNume|$at"]
                ?: hartaConsumabileNomenclator[dNume]
                ?: emptyList()

            if (consumabileAsociate.isEmpty()) continue

            val ts = (prod["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            cal.timeInMillis = ts
            val an = cal.get(Calendar.YEAR)
            val sapt = cal.get(Calendar.WEEK_OF_YEAR)
            val luna = cal.get(Calendar.MONTH) + 1
            val cheieSapt = String.format(Locale.getDefault(), "%d-S%02d", an, sapt)
            val cheieLuna = String.format(Locale.getDefault(), "%d-M%02d", an, luna)

            for (c in consumabileAsociate) {
                val cNume = c["numeConsumabil"]?.toString()?.trim() ?: continue
                val cNorma = (c["normaPerBuc"] as? Number)?.toDouble() ?: 0.0
                val cUM = c["unitate"]?.toString() ?: "buc"
                if (cNorma <= 0.0) continue

                val cantitateConsumata = cantitateOk * cNorma
                listaRezultate.add(
                    mapOf(
                        "tipTranzactie" to "consum",
                        "numeConsumabil" to cNume,
                        "cantitate" to cantitateConsumata,
                        "unitate" to cUM,
                        "operator" to (prod["operator"]?.toString() ?: "Operator"),
                        "reperSauModel" to (prod["denumirePiesa"]?.toString() ?: rId),
                        "articolId" to rId,
                        "cantitatePieseProduse" to cantitateOk.toInt(),
                        "timestamp" to ts,
                        "saptamana" to cheieSapt,
                        "luna" to cheieLuna
                    )
                )
            }
        }
        listaRezultate
    }

    // Combinăm istoricul: dacă există consumuri directe în istoric_consumabile le folosim,
    // plus orice consum retroactiv generat din producția anterioară
    val istoricConsumTotal = remember(istoricConsum, consumuriRetroactiveDinProductie) {
        val doarIesiriDirecte = istoricConsum.filter { it["tipTranzactie"]?.toString() == "consum" || it["tipTranzactie"]?.toString() == "iesire" }
        if (doarIesiriDirecte.isEmpty()) {
            consumuriRetroactiveDinProductie
        } else {
            // Unim fără duplicări evidente
            val setDirecte = doarIesiriDirecte.map { "${it["articolId"]}_${it["timestamp"]}_${it["numeConsumabil"]}" }.toSet()
            val retroactiveNoi = consumuriRetroactiveDinProductie.filter {
                val cheie = "${it["articolId"]}_${it["timestamp"]}_${it["numeConsumabil"]}"
                !setDirecte.contains(cheie)
            }
            doarIesiriDirecte + retroactiveNoi
        }
    }

    // Calcul date filtrate și grupate
    val toateConsumabileleUnice = remember(istoricConsumTotal, listaStocuriConsumabile) {
        val dinIstoric = istoricConsumTotal.mapNotNull { it["numeConsumabil"]?.toString()?.trim() }
        val dinStoc = listaStocuriConsumabile.mapNotNull { it["numeConsumabil"]?.toString()?.trim() }
        (dinIstoric + dinStoc).filter { it.isNotEmpty() }.distinct().sorted()
    }

    val consumuriFiltrate = remember(istoricConsumTotal, filtruConsumabilSelectat) {
        istoricConsumTotal.filter { doc ->
            val nume = doc["numeConsumabil"]?.toString() ?: ""
            val sePotrivesteFiltru = filtruConsumabilSelectat == "TOATE" || nume.equals(filtruConsumabilSelectat, ignoreCase = true)
            sePotrivesteFiltru
        }
    }

    val grupuriSaptamana = remember(consumuriFiltrate) {
        consumuriFiltrate.groupBy { doc ->
            doc["saptamana"]?.toString() ?: "Necunoscut"
        }.toList().sortedByDescending { it.first }
    }

    val grupuriLuna = remember(consumuriFiltrate) {
        consumuriFiltrate.groupBy { doc ->
            doc["luna"]?.toString() ?: "Necunoscut"
        }.toList().sortedByDescending { it.first }
    }

    val doarNoteIntrare = remember(istoricConsum) {
        istoricConsum.filter { it["tipTranzactie"]?.toString() == "intrare" || it["tipTranzactie"]?.toString() == "ajustare_inventar" }
            .sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
    }

    // Funcție pentru sincronizarea și actualizarea automată a stocului retroactiv în Firebase
    fun sincronizeazaStocuriRetroactiv() {
        seIncarca = true
        // Calculăm consumul total din tot istoricul pentru fiecare consumabil
        val totalConsumPerConsumabil = consumuriRetroactiveDinProductie.groupBy { it["numeConsumabil"]?.toString()?.trim()?.lowercase() ?: "" }
            .mapValues { (_, inreg) ->
                val cant = inreg.sumOf { (it["cantitate"] as? Number)?.toDouble() ?: 0.0 }
                val um = inreg.firstOrNull()?.get("unitate")?.toString() ?: "buc"
                val nume = inreg.firstOrNull()?.get("numeConsumabil")?.toString() ?: ""
                Triple(nume, cant, um)
            }

        // Verificăm dacă sunt intrări în istoric_consumabile
        val totalIntrariPerConsumabil = istoricConsum.filter { it["tipTranzactie"]?.toString() == "intrare" }
            .groupBy { it["numeConsumabil"]?.toString()?.trim()?.lowercase() ?: "" }
            .mapValues { (_, inreg) ->
                inreg.sumOf { (it["cantitate"] as? Number)?.toDouble() ?: 0.0 }
            }

        totalConsumPerConsumabil.forEach { (cheieDoc, triple) ->
            if (cheieDoc.isNotEmpty()) {
                val nume = triple.first
                val totalConsumat = triple.second
                val um = triple.third
                val intrari = totalIntrariPerConsumabil[cheieDoc] ?: 0.0
                val stocRamas = (intrari - totalConsumat).coerceAtLeast(0.0)
                val idDoc = cheieDoc.replace(" ", "_")

                firestore.collection("stocuri_consumabile").document(idDoc).set(
                    mapOf(
                        "numeConsumabil" to nume,
                        "unitate" to um,
                        "stocDisponibil" to stocRamas,
                        "totalConsumatRetroactiv" to totalConsumat,
                        "totalIntrari" to intrari,
                        "ultimulUpdate" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
            }
        }
        seIncarca = false
        statusMesaj = "Sincronizare retroactivă finalizată! Toate consumurile din producție au fost recalculate."
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Consumabile & Auxiliare", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                        Text("Gestiune stocuri, intrări și rapoarte consum", fontSize = 12.sp, color = Color.Gray)
                    }
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab-uri navigare
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = tabSelectat == "STOCURI",
                        onClick = { tabSelectat = "STOCURI" },
                        label = { Text("📦 Stocuri", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = tabSelectat == "ISTORIC_INTRARI",
                        onClick = { tabSelectat = "ISTORIC_INTRARI" },
                        label = { Text("📑 Intrări", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = tabSelectat == "RAPORT_SAPTAMANA",
                        onClick = { tabSelectat = "RAPORT_SAPTAMANA" },
                        label = { Text("📅 Săpt.", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        modifier = Modifier.weight(0.9f)
                    )
                    FilterChip(
                        selected = tabSelectat == "RAPORT_LUNA",
                        onClick = { tabSelectat = "RAPORT_LUNA" },
                        label = { Text("📊 Lunar", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mesaj status
            if (statusMesaj.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (statusMesaj.contains("Eroare")) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)),
                        modifier = Modifier.fillMaxWidth().clickable { statusMesaj = "" }
                    ) {
                        Text(
                            text = statusMesaj,
                            color = if (statusMesaj.contains("Eroare")) Color(0xFFC62828) else Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Buton Recalculare & Sincronizare Retroactivă
            item {
                OutlinedButton(
                    onClick = { sincronizeazaStocuriRetroactiv() },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00695C)),
                    enabled = !seIncarca
                ) {
                    Text("🔄 RECALCULEAZĂ TOATE CONSUMURILE RETROACTIV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ==================== TAB 1: STOCURI LIVE & INTRARE ====================
            if (tabSelectat == "STOCURI") {
                item {
                    Button(
                        onClick = { afiseazaDialogIntrare = !afiseazaDialogIntrare },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (afiseazaDialogIntrare) "ASCUNDE FORMULAR INTRARE ▲" else "➕ ADĂUGARE / INTRARE STOC NOU CONSUMABIL", fontWeight = FontWeight.Bold)
                    }
                }

                // Formular Intrare
                if (afiseazaDialogIntrare) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.5.dp, Color(0xFF00695C), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Aprovizionare Consumabil (Intrare Depozit)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF00695C))
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = numeConsumabilIntrare,
                                    onValueChange = { numeConsumabilIntrare = it },
                                    label = { Text("Denumire (ex: Aracet D3, Lac lucios, Șurub 4x35)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = cantitateIntrareText,
                                        onValueChange = { cantitateIntrareText = it },
                                        label = { Text("Cantitate sosită") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1.2f),
                                        singleLine = true
                                    )
                                    // Selector UM
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Unitate:", fontSize = 11.sp, color = Color.Gray)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("kg", "L", "buc", "gr").forEach { um ->
                                                FilterChip(
                                                    selected = unitateMasuraIntrare == um,
                                                    onClick = { unitateMasuraIntrare = um },
                                                    label = { Text(um, fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = observatiiIntrare,
                                    onValueChange = { observatiiIntrare = it },
                                    label = { Text("Observații / Furnizor (opțional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { adaugaIntrareStoc() },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                    enabled = !seIncarca
                                ) {
                                    Text("SALVEAZĂ INTRAREA ÎN DEPOZIT 💾", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Stocuri Curente Consumabile & Auxiliare (Apasă pe un card pentru intrare rapidă):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (listaStocuriConsumabile.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                            Text("Nu există consumabile înregistrate încă. Folosiți formularul de mai sus pentru a adăuga primul stoc sau asociați consumabile la repere în Nomenclator BOM.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }

                items(listaStocuriConsumabile) { stocDoc ->
                    val nume = stocDoc["numeConsumabil"]?.toString() ?: "Consumabil"
                    val cant = (stocDoc["stocDisponibil"] as? Number)?.toDouble() ?: 0.0
                    val um = stocDoc["unitate"]?.toString() ?: "buc"
                    val esteScazut = cant <= 5.0
                    val esteSelectatPentruIntrare = afiseazaDialogIntrare && numeConsumabilIntrare.equals(nume, ignoreCase = true)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                numeConsumabilIntrare = nume
                                unitateMasuraIntrare = um
                                cantitateIntrareText = ""
                                observatiiIntrare = ""
                                afiseazaDialogIntrare = true
                            }
                            .border(
                                width = if (esteSelectatPentruIntrare) 2.dp else 1.dp,
                                color = if (esteSelectatPentruIntrare) Color(0xFF00695C) else if (esteScazut) Color(0xFFDC3545).copy(alpha = 0.5f) else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = if (esteSelectatPentruIntrare) Color(0xFFE0F2F1) else Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(nume, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                    Text("➕ Adaugă stoc", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (esteScazut) "⚠️ Atenție: Stoc critic scăzut!" else "Apasă pentru aprovizionare rapidă",
                                    fontSize = 11.sp,
                                    color = if (esteScazut) Color(0xFFDC3545) else Color.Gray,
                                    fontWeight = if (esteScazut) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(horizontalAlignment = Alignment.End) {
                                    val cantStr = if (cant % 1.0 == 0.0) cant.toInt().toString() else String.format(Locale.getDefault(), "%.2f", cant)
                                    Text("$cantStr $um", fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (esteScazut) Color(0xFFDC3545) else Color(0xFF00695C))
                                    Text("Disponibil", fontSize = 11.sp, color = Color.Gray)
                                }
                                // Buton mic de corectare manuală directă a stocului faptic
                                OutlinedButton(
                                    onClick = {
                                        consumabilAjustatId = stocDoc["idDoc"]?.toString() ?: nume.lowercase().replace(" ", "_")
                                        consumabilAjustatNume = nume
                                        umConsumabilAjustat = um
                                        val cInitial = if (cant % 1.0 == 0.0) cant.toInt().toString() else cant.toString()
                                        stocNouCorectatText = cInitial
                                        afiseazaDialogAjustareStoc = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("✏️ Corectează", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                }
                            }
                        }
                    }
                }
            }

            // Dialog / Modal pentru Corectare Manuală Stoc
            if (afiseazaDialogAjustareStoc) {
                item {
                    AlertDialog(
                        onDismissRequest = { afiseazaDialogAjustareStoc = false },
                        title = { Text("Corectează Stocul Faptic", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Ajustați stocul real existent în depozit pentru '$consumabilAjustatNume':", fontSize = 12.sp, color = Color.DarkGray)
                                OutlinedTextField(
                                    value = stocNouCorectatText,
                                    onValueChange = { stocNouCorectatText = it },
                                    label = { Text("Cantitate corectată ($umConsumabilAjustat)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { salveazaAjustareStoc() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                enabled = !seIncarca
                            ) { Text("SALVEAZĂ CORECTURA") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { afiseazaDialogAjustareStoc = false }) { Text("ANULEAZĂ") }
                        }
                    )
                }
            }

            // ==================== TAB 2: ISTORIC INTRĂRI & ANULARE GREȘELI RECEPȚIE ====================
            if (tabSelectat == "ISTORIC_INTRARI") {
                item {
                    Text("Istoric Note de Intrare / Recepție (Apasă 'Anulează' dacă ai greșit cantitatea):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (doarNoteIntrare.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                            Text("Nu există note de recepție înregistrate până în prezent.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }

                items(doarNoteIntrare) { nota ->
                    val idDocIstoric = nota["idDoc"]?.toString() ?: ""
                    val cNume = nota["numeConsumabil"]?.toString() ?: "Consumabil"
                    val cCant = (nota["cantitate"] as? Number)?.toDouble() ?: 0.0
                    val cUM = nota["unitate"]?.toString() ?: ""
                    val op = nota["operator"]?.toString() ?: ""
                    val obs = nota["observatii"]?.toString() ?: ""
                    val ts = (nota["timestamp"] as? Number)?.toLong() ?: 0L
                    val dataOraStr = if (ts > 0L) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(java.util.Date(ts)) else ""
                    val tip = nota["tipTranzactie"]?.toString() ?: ""

                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (tip == "ajustare_inventar") "⚙️ $cNume (Reglare Stoc)" else "📥 $cNume (+${if (cCant % 1.0 == 0.0) cCant.toInt().toString() else cCant} $cUM)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (tip == "ajustare_inventar") Color(0xFFE65100) else Color(0xFF00695C)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Recepționat de: $op | $dataOraStr", fontSize = 11.sp, color = Color.Gray)
                                if (obs.isNotEmpty()) {
                                    Text("Obs: $obs", fontSize = 11.sp, color = Color.DarkGray)
                                }
                            }
                            if (tip == "intrare" && idDocIstoric.isNotEmpty()) {
                                Button(
                                    onClick = { anuleazaNotaIntrare(idDocIstoric, cNume, cCant) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    enabled = !seIncarca
                                ) {
                                    Text("Anulează 🗑️", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // ==================== TAB 2 & 3: RAPOARTE CONSUM (SĂPTĂMÂNAL / LUNAR) ====================
            if (tabSelectat == "RAPORT_SAPTAMANA" || tabSelectat == "RAPORT_LUNA") {
                val esteRaportSaptamanal = tabSelectat == "RAPORT_SAPTAMANA"

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (esteRaportSaptamanal) "Filtru Consumabil (Raport Săptămânal):" else "Filtru Consumabil (Raport Lunar):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = filtruConsumabilSelectat == "TOATE",
                                onClick = { filtruConsumabilSelectat = "TOATE" },
                                label = { Text("Toate", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            toateConsumabileleUnice.take(4).forEach { cNume ->
                                FilterChip(
                                    selected = filtruConsumabilSelectat == cNume,
                                    onClick = { filtruConsumabilSelectat = cNume },
                                    label = { Text(cNume, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                val grupuriPePerioada = if (esteRaportSaptamanal) grupuriSaptamana else grupuriLuna

                if (grupuriPePerioada.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                            Text("Nu există consumuri raportate în producție pentru această perioadă / filtru.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }

                items(grupuriPePerioada) { (perioada, listaInregistrari) ->
                    // Calcul total pe consumabil în această perioadă
                    val totaluriPerConsumabil = listaInregistrari.groupBy { it["numeConsumabil"]?.toString() ?: "Necunoscut" }
                        .mapValues { (_, inreg) ->
                            val cantTotala = inreg.sumOf { (it["cantitate"] as? Number)?.toDouble() ?: 0.0 }
                            val um = inreg.firstOrNull()?.get("unitate")?.toString() ?: ""
                            cantTotala to um
                        }

                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00695C).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (esteRaportSaptamanal) "📅 Săptămâna $perioada" else "📊 Luna $perioada",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color(0xFF00695C)
                                )
                                Text("${listaInregistrari.size} operațiuni", fontSize = 11.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Total consumat în perioada:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(4.dp))

                            totaluriPerConsumabil.forEach { (numeC, pereche) ->
                                val cantTotala = pereche.first
                                val um = pereche.second
                                val cantStr = if (cantTotala % 1.0 == 0.0) cantTotala.toInt().toString() else String.format(Locale.getDefault(), "%.2f", cantTotala)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• $numeC", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("$cantStr $um", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            // Detaliu ultimele operațiuni de raportare care au generat acest consum
                            Text("Exemple raportări recente din această perioadă:", fontSize = 11.sp, color = Color.Gray)
                            listaInregistrari.take(5).forEach { reg ->
                                val piesaSauScaun = reg["reperSauModel"]?.toString() ?: reg["articolId"]?.toString() ?: ""
                                val cantPiese = reg["cantitatePieseProduse"]?.toString() ?: ""
                                val cantConsumata = (reg["cantitate"] as? Number)?.toDouble() ?: 0.0
                                val um = reg["unitate"]?.toString() ?: ""
                                val op = reg["operator"]?.toString() ?: ""
                                val nCons = reg["numeConsumabil"]?.toString() ?: ""
                                val cConsStr = if (cantConsumata % 1.0 == 0.0) cantConsumata.toInt().toString() else String.format(Locale.getDefault(), "%.2f", cantConsumata)
                                Text(
                                    text = "  - $piesaSauScaun ($cantPiese buc făcute) ➔ -$cConsStr $um $nCons ($op)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF424242)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}
