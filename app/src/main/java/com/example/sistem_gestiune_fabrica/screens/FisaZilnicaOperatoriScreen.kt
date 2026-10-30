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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class IntrareRaportareIndividuala(
    val cantitate: Int,
    val ora: String
)

data class DetaliuPiesaOperator(
    val reperId: String,
    val denumirePiesa: String,
    val modelProdus: String,
    val atelier: String,
    val cantitateTotala: Int,
    val numarRaportari: Int,
    val ultimaOra: String,
    val intrariIndividuale: List<IntrareRaportareIndividuala>
)

data class FisaOperatorZilnica(
    val username: String,
    val numeComplet: String,
    val functie: String,
    val totalPiese: Int,
    val totalRaportari: Int,
    val detaliiPiese: List<DetaliuPiesaOperator>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FisaZilnicaOperatoriScreen(
    firestore: FirebaseFirestore,
    userData: Map<String, Any>,
    onBackClick: () -> Unit
) {
    var istoricProductieTotal by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaUtilizatoriSistem by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaComenziProductie by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var seIncarca by remember { mutableStateOf(true) }

    // Calendar & Ziua selectată (implicit AZI)
    val calendarSelectat = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var timestampZiSelectata by remember { mutableStateOf(calendarSelectat.timeInMillis) }
    var operatorSelectatFiltru by remember { mutableStateOf("TOTS") } // "TOTS" sau username specific
    val carduriExtinse = remember { mutableStateMapOf<String, Boolean>() }

    // Ascultăm live datele din Firestore
    LaunchedEffect(Unit) {
        firestore.collection("utilizatori_sistem").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaUtilizatoriSistem = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaComenziProductie = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                istoricProductieTotal = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDocRaport" to it.id) }
                seIncarca = false
            }
        }
    }

    // Calculăm intervalul zilei selectate (start: 00:00:00, end: 23:59:59.999)
    val startOfDay = remember(timestampZiSelectata) {
        Calendar.getInstance().apply {
            timeInMillis = timestampZiSelectata
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val endOfDay = remember(timestampZiSelectata) {
        Calendar.getInstance().apply {
            timeInMillis = timestampZiSelectata
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    // Raportări filtrate pe ziua selectată
    val raportariZiCurenta = remember(istoricProductieTotal, startOfDay, endOfDay) {
        istoricProductieTotal.filter { raport ->
            val ts = (raport["timestamp"] as? Long) ?: 0L
            ts in startOfDay..endOfDay
        }
    }

    // Mapare date utilizator (username -> nume complet & funcție)
    val mapUtilizatori = remember(listaUtilizatoriSistem) {
        val map = mutableMapOf<String, Pair<String, String>>()
        listaUtilizatoriSistem.forEach { u ->
            val uname = u["username"]?.toString()?.trim()?.lowercase() ?: ""
            val nume = u["nume"]?.toString()?.trim() ?: uname
            val functie = u["functie"]?.toString()?.trim() ?: "Operator"
            if (uname.isNotEmpty()) {
                map[uname] = Pair(nume, functie)
            }
        }
        map
    }

    // Mapare ReperId / ArticolId -> Nume Produs (Model Scaun) din rețetele BOM și comenzi
    val mapReperLaProdus = remember(listaComenziProductie) {
        val map = mutableMapOf<String, String>()
        listaComenziProductie.forEach { comanda ->
            val modelNume = comanda["modelNume"]?.toString() ?: "Scaun"
            val modelId = comanda["modelId"]?.toString() ?: ""
            if (modelId.isNotEmpty()) {
                map[modelId] = modelNume
            }
            val retetaBOM = comanda["retetaBOM"] as? List<*> ?: emptyList<Any>()
            retetaBOM.forEach { compAny ->
                val comp = compAny as? Map<*, *> ?: return@forEach
                val reperId = comp["reperId"]?.toString() ?: ""
                if (reperId.isNotEmpty() && !map.containsKey(reperId)) {
                    map[reperId] = modelNume
                }
            }
        }
        map
    }

    // Identificare drepturi utilizator logat
    val usernameLogat = remember(userData) {
        userData["username"]?.toString()?.trim()?.lowercase() ?: ""
    }
    val functieLogat = remember(userData) {
        userData["functie"]?.toString()?.uppercase() ?: ""
    }
    val drepturiFerestre = remember(userData) {
        userData["drepturiFerestre"] as? Map<*, *> ?: emptyMap<Any, Any>()
    }
    val esteAdmin = remember(functieLogat, drepturiFerestre) {
        functieLogat.contains("ADMIN") ||
                functieLogat.contains("MAISTRU") ||
                drepturiFerestre["administrare_drepturi"] == true ||
                drepturiFerestre["administrare_drepturi"]?.toString() == "true"
    }

    // Agregare pe operatori
    val fiseOperatori = remember(raportariZiCurenta, mapUtilizatori, mapReperLaProdus, operatorSelectatFiltru, esteAdmin, usernameLogat) {
        val grupatiPeOperator = raportariZiCurenta.groupBy {
            it["operator"]?.toString()?.trim()?.lowercase() ?: "necunoscut"
        }

        val lista = grupatiPeOperator.map { (operatorUsername, rapoarte) ->
            val infoUser = mapUtilizatori[operatorUsername]
            val numeComplet = infoUser?.first ?: operatorUsername.replaceFirstChar { it.uppercase() }
            val functie = infoUser?.second ?: "Operator"

            val totalPiese = rapoarte.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
            val totalRaportari = rapoarte.size

            // Grupăm pe piese realizate de acest operator (pe baza reperId + atelier)
            val pieseGrupate = rapoarte.groupBy {
                val repId = it["articolId"]?.toString() ?: "Nespecificat"
                val at = it["atelier"]?.toString() ?: "Atelier"
                "$repId|$at"
            }.map { (_, rapoartePiesa) ->
                val primaFisa = rapoartePiesa.first()
                val repId = primaFisa["articolId"]?.toString() ?: ""
                val denumire = primaFisa["denumirePiesa"]?.toString() ?: repId
                val atelier = primaFisa["atelier"]?.toString() ?: "Atelier"

                // Căutăm denumirea produsului în raportările piesei sau în mapare
                val modelGasit = rapoartePiesa.mapNotNull {
                    it["modelNumeScaun"]?.toString() ?: it["modelNume"]?.toString()
                }.firstOrNull { it.isNotEmpty() }
                    ?: mapReperLaProdus[repId]
                    ?: if (atelier == "Atelier0") "Semifabricat Comun" else "Produs Fabrică"

                val cantitatePiesaTotala = rapoartePiesa.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
                val ultimulTs = rapoartePiesa.mapNotNull { it["timestamp"] as? Long }.maxOrNull() ?: 0L
                val oraStr = if (ultimulTs > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ultimulTs)) else "--:--"

                // Extragem fiecare înregistrare individuală cronologic
                val intrari = rapoartePiesa.sortedBy { (it["timestamp"] as? Long) ?: 0L }.map { r ->
                    val q = (r["cantitateOk"] as? Long)?.toInt() ?: 0
                    val ts = (r["timestamp"] as? Long) ?: 0L
                    val o = if (ts > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)) else "--:--"
                    IntrareRaportareIndividuala(cantitate = q, ora = o)
                }

                DetaliuPiesaOperator(
                    reperId = repId,
                    denumirePiesa = denumire,
                    modelProdus = modelGasit,
                    atelier = atelier,
                    cantitateTotala = cantitatePiesaTotala,
                    numarRaportari = rapoartePiesa.size,
                    ultimaOra = oraStr,
                    intrariIndividuale = intrari
                )
            }.sortedByDescending { it.cantitateTotala }

            FisaOperatorZilnica(
                username = operatorUsername,
                numeComplet = numeComplet,
                functie = functie,
                totalPiese = totalPiese,
                totalRaportari = totalRaportari,
                detaliiPiese = pieseGrupate
            )
        }.sortedByDescending { it.totalPiese }

        // Dacă nu este Admin, utilizatorul își vede EXCLUSIV fișa proprie
        if (!esteAdmin) {
            lista.filter { it.username == usernameLogat }
        } else if (operatorSelectatFiltru == "TOTS") {
            lista
        } else {
            lista.filter { it.username == operatorSelectatFiltru.lowercase() }
        }
    }

    // Statistici globale zi
    val totalPieseFabricaAzi = remember(raportariZiCurenta) {
        raportariZiCurenta.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
    }
    val totalOperatoriActivi = remember(raportariZiCurenta) {
        raportariZiCurenta.mapNotNull { it["operator"]?.toString()?.trim()?.lowercase() }.distinct().size
    }

    val formatDataZi = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("ro", "RO")) }
    val stringDataCurenta = formatDataZi.format(Date(timestampZiSelectata)).replaceFirstChar { it.uppercase() }

    val esteAstazi = remember(timestampZiSelectata) {
        val aziCal = Calendar.getInstance()
        val selCal = Calendar.getInstance().apply { timeInMillis = timestampZiSelectata }
        aziCal.get(Calendar.YEAR) == selCal.get(Calendar.YEAR) &&
                aziCal.get(Calendar.DAY_OF_YEAR) == selCal.get(Calendar.DAY_OF_YEAR)
    }

    val operatoriUnici = remember(raportariZiCurenta) {
        raportariZiCurenta.mapNotNull { it["operator"]?.toString()?.trim()?.lowercase() }.distinct().sorted()
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
                        imageVector = Icons.Default.ArrowBack,
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
            // --- HEADER ---
            item {
                Text(
                    text = "Fișă Zilnică Operatori 📑",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00695C)
                )
                Text(
                    text = "Totalizator producție realizată pe fiecare utilizator",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Divider(color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
            }

            // --- SELECTOR ZI ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Rând 1: Butoane navigare stânga / dreapta proporționale
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val c = Calendar.getInstance().apply {
                                        timeInMillis = timestampZiSelectata
                                        add(Calendar.DAY_OF_YEAR, -1)
                                    }
                                    timestampZiSelectata = c.timeInMillis
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("◀ ZIUA ANT.", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val c = Calendar.getInstance().apply {
                                        timeInMillis = timestampZiSelectata
                                        add(Calendar.DAY_OF_YEAR, 1)
                                    }
                                    timestampZiSelectata = c.timeInMillis
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("ZIUA URM. ▶", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        // Rând 2: Data afișată centrat clar și elegant
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (esteAstazi) "ASTĂZI (LIVE)" else "ISTORIC ZI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (esteAstazi) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringDataCurenta,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF004D40)
                            )
                        }

                        // Rând 3: Buton revenire la ziua de astăzi
                        if (!esteAstazi) {
                            Button(
                                onClick = {
                                    val c = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    timestampZiSelectata = c.timeInMillis
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("SARI LA ZIUA DE ASTĂZI ↩", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // --- SUMAR ZI (DOAR ADMINUL VEDE TOTALUL GLOBAL AL FABRICII) ---
            if (esteAdmin) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "TOTAL FABRICĂ ZI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text(text = "$totalPieseFabricaAzi buc", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
                                Text(text = "${raportariZiCurenta.size} raportări înregistrate", fontSize = 10.sp, color = Color.Gray)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "OPERATORI ACTIVI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                Text(text = "$totalOperatoriActivi persoane", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF0D47A1))
                                Text(text = "cu activitate raportată azi", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // --- FILTRU PE OPERATOR (DOAR PENTRU ADMIN) ---
            if (esteAdmin && operatoriUnici.isNotEmpty()) {
                item {
                    Text(text = "Filtrează după operator:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = operatorSelectatFiltru == "TOTS",
                            onClick = { operatorSelectatFiltru = "TOTS" },
                            label = { Text("Toți (${operatoriUnici.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        operatoriUnici.forEach { opUser ->
                            val numeAfis = mapUtilizatori[opUser]?.first?.split(" ")?.firstOrNull() ?: opUser
                            FilterChip(
                                selected = operatorSelectatFiltru == opUser,
                                onClick = {
                                    operatorSelectatFiltru = if (operatorSelectatFiltru == opUser) "TOTS" else opUser
                                },
                                label = { Text(numeAfis, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }

            // --- STATUS ÎNCĂRCARE ---
            if (seIncarca) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00695C))
                    }
                }
            }

            // --- MESAJ DACĂ NU SUNT RAPORTĂRI ---
            if (!seIncarca && fiseOperatori.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Nu s-au găsit raportări de producție pentru această zi.", fontWeight = FontWeight.Bold, color = Color(0xFFE65100), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Dacă s-au lucrat piese, asigurați-vă că operatorii au apăsat 'Confirmă raportarea' în ecranul de producție.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // --- LISTĂ CARDS OPERATORI ---
            items(fiseOperatori) { fisa ->
                val esteExtins = carduriExtinse[fisa.username] ?: true // implicit deschis pentru vizibilitate facilă

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFB2DFDB), RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header Card Operator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    carduriExtinse[fisa.username] = !esteExtins
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = fisa.numeComplet.uppercase(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color(0xFF004D40)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFE0F2F1), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = fisa.username, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                                    }
                                }
                                Text(
                                    text = "Funcție: ${fisa.functie} | ${fisa.detaliiPiese.size} repere diferite lucrate",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${fisa.totalPiese} buc",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = "${fisa.totalRaportari} raportări",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                Icon(
                                    imageVector = if (esteExtins) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Extinde/Restrânge",
                                    tint = Color(0xFF00695C)
                                )
                            }
                        }

                        // Detaliu desfășurat cu piesele lucrate
                        AnimatedVisibility(visible = esteExtins) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Divider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(bottom = 8.dp))

                                // Header mini-tabel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("PIESĂ / REPER", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, modifier = Modifier.weight(1.4f))
                                    Text("SECTOR", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                                    Text("BUCĂȚI", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                                    Text("ORA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    fisa.detaliiPiese.forEach { piesa ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFAFCFD), RoundedCornerShape(10.dp))
                                                .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1.4f)) {
                                                    Text(
                                                        text = " ${piesa.modelProdus.uppercase()}",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF00695C)
                                                    )
                                                    Text(
                                                        text = piesa.denumirePiesa,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF212529)
                                                    )

                                                }

                                                Box(
                                                    modifier = Modifier.weight(0.8f),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val culoareBgSector = when (piesa.atelier) {
                                                        "Atelier0" -> Color(0xFFFFF3E0)
                                                        "Atelier1" -> Color(0xFFE8F5E9)
                                                        "Atelier2" -> Color(0xFFE3F2FD)
                                                        "Atelier3" -> Color(0xFFF3E5F5)
                                                        "Atelier4" -> Color(0xFFFFEBEE)
                                                        else -> Color(0xFFECEFF1)
                                                    }
                                                    val culoareTextSector = when (piesa.atelier) {
                                                        "Atelier0" -> Color(0xFFE65100)
                                                        "Atelier1" -> Color(0xFF2E7D32)
                                                        "Atelier2" -> Color(0xFF1565C0)
                                                        "Atelier3" -> Color(0xFF7B1FA2)
                                                        "Atelier4" -> Color(0xFFC62828)
                                                        else -> Color(0xFF37474F)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .background(culoareBgSector, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = piesa.atelier,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = culoareTextSector
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(0.7f), horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "${piesa.cantitateTotala} buc",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                    if (piesa.numarRaportari > 1) {
                                                        Text(
                                                            text = "(${piesa.numarRaportari} tranșe)",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFE65100)
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = piesa.ultimaOra,
                                                    fontSize = 10.sp,
                                                    color = Color.DarkGray,
                                                    modifier = Modifier.weight(0.6f),
                                                    textAlign = TextAlign.End
                                                )
                                            }

                                            // Dacă reperul a fost raportat în mai multe tranșe / ore diferite în aceeași zi, afișăm istoricul defalcat
                                            if (piesa.numarRaportari > 1) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFFF1F8E9), RoundedCornerShape(6.dp))
                                                        .border(0.5.dp, Color(0xFFC5E1A5), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Defalcare tranșe: " + piesa.intrariIndividuale.joinToString(" + ") { "${it.cantitate} buc (${it.ora})" },
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF33691E)
                                                    )
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
}
