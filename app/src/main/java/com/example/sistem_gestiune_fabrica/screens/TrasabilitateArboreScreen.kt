package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ReperDetaliuTrasabilitate(
    val reperId: String,
    val denumire: String,
    val bucPerScaun: Int,
    val tintaTotala: Int,
    val realizat: Int,
    val stocPeRaft: Int,
    val procent: Float,
    val atelier: String,
    val fazaPrelucrare: String,
    val subansambluDestinatie: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrasabilitateArboreScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var listaComenzi by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaStocuriFaze by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var listaIstoricProductie by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var modelSelectatId by remember { mutableStateOf("") }
    var reperSelectatPtDetalii by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
                listaComenzi = list
                if (modelSelectatId.isEmpty() && list.isNotEmpty()) {
                    val primulPlan = list.find { it["stare"]?.toString() == "In Lucru" } ?: list.first()
                    modelSelectatId = primulPlan["modelId"]?.toString() ?: ""
                }
            }
        }
        firestore.collection("stocuri_faze").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaStocuriFaze = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaIstoricProductie = snapshot.documents.map { it.data ?: emptyMap() }
            }
        }
    }

    val modelActiv = listaComenzi.find { it["modelId"]?.toString() == modelSelectatId }
    val retetaBOM = (modelActiv?.get("retetaBOM") as? List<*>) ?: emptyList<Any>()
    val cantitateScauneTinta = (modelActiv?.get("cantitateScaune") as? Long)?.toInt() ?: 0
    val modelNumeText = modelActiv?.get("modelNume")?.toString() ?: "Produs"

    // Functie de extragere detalii piesa
    fun parseazaReper(nod: Any?): ReperDetaliuTrasabilitate {
        val map = nod as? Map<*, *> ?: emptyMap<Any, Any>()
        val rId = map["reperId"]?.toString() ?: ""
        val denumire = map["denumirePiesa"]?.toString() ?: rId
        val buc = (map["cantitateNecesara"] as? Long)?.toInt() ?: 1
        val atelier = map["atelierAsociat"]?.toString() ?: "Atelier1"
        val faza = map["fazaPrelucrare"]?.toString() ?: ""
        val subansamblu = map["subansambluDestinatie"]?.toString() ?: ""

        val tintaTotala = cantitateScauneTinta * buc
        val realizat = listaIstoricProductie
            .filter { it["articolId"]?.toString() == rId && it["atelier"]?.toString() == atelier }
            .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }

        val idDocStoc = "${rId}_$atelier"
        val stocPeRaft = (listaStocuriFaze.find { it["idDoc"] == idDocStoc }?.get("stocDisponibil") as? Long)?.toInt() ?: 0

        // Cantitate totală acoperită = Realizat în producție + Stoc fizic disponibil pe raft
        val disponibilTotal = realizat + stocPeRaft
        val procent = if (tintaTotala > 0) (disponibilTotal.toFloat() / tintaTotala).coerceIn(0f, 1f) else 0f

        return ReperDetaliuTrasabilitate(
            reperId = rId,
            denumire = denumire,
            bucPerScaun = buc,
            tintaTotala = tintaTotala,
            realizat = realizat,
            stocPeRaft = stocPeRaft,
            procent = procent,
            atelier = atelier,
            fazaPrelucrare = faza,
            subansambluDestinatie = subansamblu
        )
    }

    val repereParsed = retetaBOM.map { parseazaReper(it) }

    // Separare pe etapele reale din rețetă
    val pieseAtelier0 = repereParsed.filter { it.atelier == "Atelier0" }
    val pieseAtelier1 = repereParsed.filter { it.atelier == "Atelier1" }
    val pieseAtelier2 = repereParsed.filter { it.atelier == "Atelier2" }
    val pieseAtelier3 = repereParsed.filter { it.atelier == "Atelier3" }
    val pieseAtelier4 = repereParsed.filter { it.atelier == "Atelier4" }

    // Produs Finit final (Atelier 4)
    val realizatProdusFinit = listaIstoricProductie
        .filter { (it["articolId"]?.toString() == modelSelectatId || it["modelId"]?.toString() == modelSelectatId) && it["atelier"]?.toString() == "Atelier4" }
        .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
    val procentFinal = if (cantitateScauneTinta > 0) (realizatProdusFinit.toFloat() / cantitateScauneTinta).coerceIn(0f, 1f) else 0f

    // Medii procentuale pe ateliere pentru detectarea gâtuirii (Bottleneck)
    fun medieProcent(lista: List<ReperDetaliuTrasabilitate>): Float {
        if (lista.isEmpty()) return 1f
        return lista.map { it.procent }.average().toFloat()
    }

    val procAt0 = medieProcent(pieseAtelier0)
    val procAt1 = medieProcent(pieseAtelier1)
    val procAt2 = medieProcent(pieseAtelier2)
    val procAt3 = medieProcent(pieseAtelier3)
    val procAt4 = if (cantitateScauneTinta > 0) procentFinal else 1f

    // Identificare atelier cu cel mai mic procent activ (GÂTUIRE / BOTTLENECK)
    val ateliereActive = mutableListOf<Pair<String, Float>>()
    if (pieseAtelier0.isNotEmpty()) ateliereActive.add("Atelier0" to procAt0)
    if (pieseAtelier1.isNotEmpty()) ateliereActive.add("Atelier1" to procAt1)
    if (pieseAtelier2.isNotEmpty()) ateliereActive.add("Atelier2" to procAt2)
    if (pieseAtelier3.isNotEmpty()) ateliereActive.add("Atelier3" to procAt3)
    ateliereActive.add("Atelier4" to procAt4)

    // Identificăm reperul specific cel mai întârziat din întreg fluxul (cauza rădăcină a gâtuirii)
    val reperCauzaGatuire = repereParsed.filter { it.procent < 1f }.minByOrNull { it.procent }

    val celMaiMicProcent = ateliereActive.minByOrNull { it.second }?.second ?: 1f
    val codAtelierGatuire = if (celMaiMicProcent < 1f) ateliereActive.minByOrNull { it.second }?.first else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7FB))
            .padding(16.dp)
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🔀 TRASABILITATE & FLUX TEHNOLOGIC",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0D47A1)
                )

            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        genereazaSiDeschideRaportPdfA4(
                            context = context,
                            listaComenzi = listaComenzi,
                            listaStocuriFaze = listaStocuriFaze,
                            listaIstoric = listaIstoricProductie
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("📄 EXPORT PDF A4", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.White)
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
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- SELECTOR PRODUSE ---
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listaComenzi.distinctBy { it["modelId"]?.toString() }.forEach { comanda ->
                val mId = comanda["modelId"]?.toString() ?: ""
                val mNume = comanda["modelNume"]?.toString() ?: "Produs"
                val esteSelectat = (modelSelectatId == mId)
                FilterChip(
                    selected = esteSelectat,
                    onClick = { modelSelectatId = mId },
                    label = { Text(mNume.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- BANNER GÂTUIRE REALĂ CU REPERUL CAUZĂ PRINCIPALĂ ---
        if (reperCauzaGatuire != null) {
            val numeAtelierBlocat = when(reperCauzaGatuire.atelier) {
                "Atelier0" -> "Atelierul 0 (Prelucrare)"
                "Atelier1" -> "Atelierul 1 (Debitare / Print)"
                "Atelier2" -> "Atelierul 2 (Montaj)"
                "Atelier3" -> "Atelierul 3 (Vopsitorie)"
                "Atelier4" -> "Atelierul 4 (Ambalaj)"
                else -> reperCauzaGatuire.atelier
            }

            // Găsim denumirea comercială a subansamblului țintă (nu codul intern)
            val denumireComercialaSubansamblu = if (reperCauzaGatuire.subansambluDestinatie.isNotEmpty()) {
                val gasit = repereParsed.find { it.reperId.equals(reperCauzaGatuire.subansambluDestinatie, ignoreCase = true) }
                gasit?.denumire ?: reperCauzaGatuire.subansambluDestinatie.replace("_", " ").replace("-", " ")
            } else ""

            val explicatieDestinatie = if (denumireComercialaSubansamblu.isNotEmpty()) {
                "blochează garniturarea ansamblului '$denumireComercialaSubansamblu'"
            } else {
                "blochează ambalarea finală a scaunului"
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🛑", fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("GÂTUIRE CRITICĂ IDENTIFICATĂ!", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFD32F2F))
                        val totalDisponibil = reperCauzaGatuire.realizat + reperCauzaGatuire.stocPeRaft
                        Text(
                            text = "Reperul '${reperCauzaGatuire.denumire}' din $numeAtelierBlocat $explicatieDestinatie (Disponibil cu stoc: $totalDisponibil / ${reperCauzaGatuire.tintaTotala} buc  ${(reperCauzaGatuire.procent * 100).toInt()}%)!",
                            fontSize = 11.sp,
                            color = Color(0xFFB71C1C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // --- ZONĂ SCROLLABILĂ A DIAGRAMEI TEHNOLOGICE ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.5.dp, Color(0xFFBBDEFB), RoundedCornerShape(16.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. CAPSULĂ START: COMANDĂ LANSARE
            BlocCapsulaStart(
                titlu = "LANSARE PLAN PRODUCȚIE",
                subtitlu = "$modelNumeText • Țintă: $cantitateScauneTinta bucăți",
                culoare = Color(0xFF0D6EFD)
            )

            // 2. ATELIERUL 0 (DOAR DACĂ ARE PIESE ÎN NOMENCLATOR)
            if (pieseAtelier0.isNotEmpty()) {
                val esteGatuire = (codAtelierGatuire == "Atelier0")
                SageataFluxCuText("Flux Prelucrare Primară")
                CardEtapaCuRepere(
                    titluEtapa = "⚙️ ATELIERUL 0: PRELUCRARE OPȚIONALĂ",
                    procentEtapa = procAt0,
                    culoareEtapa = Color(0xFF673AB7),
                    esteGatuire = esteGatuire,
                    repere = pieseAtelier0,
                    reperSelectatId = reperSelectatPtDetalii,
                    idReperBlocant = reperCauzaGatuire?.reperId,
                    onSelectReper = { rId ->
                        reperSelectatPtDetalii = if (reperSelectatPtDetalii == rId) null else rId
                    }
                )
            }

            // 3. ATELIERUL 1 (DIGITAL PRINT & DEBITARE BRUTĂ)
            if (pieseAtelier1.isNotEmpty() || repereParsed.isEmpty()) {
                val esteGatuire = (codAtelierGatuire == "Atelier1")
                SageataFluxCuText(if (pieseAtelier0.isNotEmpty()) "Piese Prelucrate ➔ Tăiere" else "Start Tăiere & Print")
                CardEtapaCuRepere(
                    titluEtapa = "🪵 ATELIERUL 1: DIGITAL PRINT & DEBITARE",
                    procentEtapa = procAt1,
                    culoareEtapa = Color(0xFF4E342E),
                    esteGatuire = esteGatuire,
                    repere = pieseAtelier1,
                    reperSelectatId = reperSelectatPtDetalii,
                    idReperBlocant = reperCauzaGatuire?.reperId,
                    onSelectReper = { rId ->
                        reperSelectatPtDetalii = if (reperSelectatPtDetalii == rId) null else rId
                    }
                )
            }

            // 4. ATELIERUL 2: SUBANSAMBLE & MONTAJ (DOAR DACĂ EXISTĂ ÎN NOMENCLATOR)
            if (pieseAtelier2.isNotEmpty()) {
                val esteGatuire = (codAtelierGatuire == "Atelier2")
                SageataFluxCuText("Alimentare Repere ➔ Asamblare Subansamble")
                CardEtapaCuRepere(
                    titluEtapa = "🔨 ATELIERUL 2: MONTAJ SUBANSAMBLE",
                    procentEtapa = procAt2,
                    culoareEtapa = Color(0xFFE65100),
                    esteGatuire = esteGatuire,
                    repere = pieseAtelier2,
                    reperSelectatId = reperSelectatPtDetalii,
                    idReperBlocant = reperCauzaGatuire?.reperId,
                    onSelectReper = { rId ->
                        reperSelectatPtDetalii = if (reperSelectatPtDetalii == rId) null else rId
                    }
                )
            }

            // 5. ATELIERUL 3: VOPSITORIE (DOAR DACĂ EXISTĂ ÎN NOMENCLATOR)
            if (pieseAtelier3.isNotEmpty()) {
                val esteGatuire = (codAtelierGatuire == "Atelier3")
                SageataFluxCuText("Subansamble / Elemente ➔ Vopsitorie & Finisaj")
                CardEtapaCuRepere(
                    titluEtapa = "🎨 ATELIERUL 3: FINISAJ & VOPSITORIE",
                    procentEtapa = procAt3,
                    culoareEtapa = Color(0xFF00838F),
                    esteGatuire = esteGatuire,
                    repere = pieseAtelier3,
                    reperSelectatId = reperSelectatPtDetalii,
                    idReperBlocant = reperCauzaGatuire?.reperId,
                    onSelectReper = { rId ->
                        reperSelectatPtDetalii = if (reperSelectatPtDetalii == rId) null else rId
                    }
                )
            }

            // 6. ATELIERUL 4: AMBALARE FINALĂ & PRODUS COMERCIAL
            val esteGatuireAt4 = (codAtelierGatuire == "Atelier4")
            SageataFluxCuText("Toate Piesele & Subansamblele Gata ➔ Ambalaj")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (esteGatuireAt4) 2.5.dp else 1.5.dp,
                        color = if (esteGatuireAt4) Color(0xFFD32F2F) else Color(0xFF1565C0),
                        shape = RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (esteGatuireAt4) Color(0xFFFFEBEE) else Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📦", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("ATELIERUL 4: TAPIȚERIE & AMBALAJ", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF1565C0))
                                Text("Ambalare cutii, montaj tapițerie și produs finit", fontSize = 11.sp, color = Color.DarkGray)
                            }
                        }
                        Text("${(procentFinal * 100).toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF1565C0))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { procentFinal },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        trackColor = Color.White.copy(alpha = 0.7f),
                        color = Color(0xFF1565C0)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Realizat: $realizatProdusFinit / $cantitateScauneTinta buc ambalate complet",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )

                    // Repere specifice din Atelierul 4 dacă există (ex: cutii, șezuturi)
                    if (pieseAtelier4.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Elemente consumate la ambalaj:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        pieseAtelier4.forEach { p ->
                            ElementReperCompact(p, reperSelectatPtDetalii == p.reperId) {
                                reperSelectatPtDetalii = if (reperSelectatPtDetalii == p.reperId) null else p.reperId
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            // 8. FINAL: LIVRARE CĂTRE CLIENT
            SageataFluxCuText("Produse Ambalate")
            BlocCapsulaStart(
                titlu = "PRODUS FINIT ➔ LIVRARE CĂTRE CLIENT",
                subtitlu = "Complet: $realizatProdusFinit din $cantitateScauneTinta buc",
                culoare = Color(0xFF2E7D32)
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// --- CARD ETAPĂ CU LISTĂ REPERE & SUBANSAMBLE ---

@Composable
fun CardEtapaCuRepere(
    titluEtapa: String,
    procentEtapa: Float,
    culoareEtapa: Color,
    esteGatuire: Boolean,
    repere: List<ReperDetaliuTrasabilitate>,
    reperSelectatId: String?,
    idReperBlocant: String?,
    onSelectReper: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (esteGatuire) 2.5.dp else 1.5.dp,
                color = if (esteGatuire) Color(0xFFD32F2F) else culoareEtapa,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (esteGatuire) Color(0xFFFFEBEE) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Etapă
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (esteGatuire) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = titluEtapa,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (esteGatuire) Color(0xFFD32F2F) else culoareEtapa
                    )
                }

                Text(
                    text = "${(procentEtapa * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = if (esteGatuire) Color(0xFFD32F2F) else culoareEtapa
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { procentEtapa },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                trackColor = Color(0xFFE9ECEF),
                color = if (esteGatuire) Color(0xFFD32F2F) else culoareEtapa
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Repere & Componente alocate (${repere.size}):",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(6.dp))

            repere.forEach { reper ->
                ElementReperCompact(
                    reper = reper,
                    esteSelectat = reperSelectatId == reper.reperId,
                    esteCauzaBlocaj = reper.reperId == idReperBlocant,
                    onClick = { onSelectReper(reper.reperId) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// --- ELEMENT COMPACT REPER DIN ETAPĂ ---

@Composable
fun ElementReperCompact(
    reper: ReperDetaliuTrasabilitate,
    esteSelectat: Boolean,
    esteCauzaBlocaj: Boolean = false,
    onClick: () -> Unit
) {
    val culoareStatus = when {
        reper.procent >= 1f -> Color(0xFF2E7D32)
        reper.procent > 0f -> Color(0xFFF57C00)
        else -> Color(0xFFD32F2F)
    }

    val esteBypassDirectLaAmbalaj = (reper.atelier == "Atelier1" || reper.atelier == "Atelier0") && reper.subansambluDestinatie.isEmpty()

    val culoareBorderCard = when {
        esteCauzaBlocaj -> Color(0xFFD32F2F)
        esteBypassDirectLaAmbalaj -> Color(0xFF1976D2)
        else -> Color(0xFFE0E0E0)
    }

    val grosimeBorder = if (esteCauzaBlocaj) 2.dp else if (esteBypassDirectLaAmbalaj) 1.5.dp else 1.dp
    val culoareFundal = if (esteCauzaBlocaj) Color(0xFFFFEBEE) else if (esteBypassDirectLaAmbalaj) Color(0xFFF0F7FF) else Color(0xFFFAFAFA)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = grosimeBorder,
                color = culoareBorderCard,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = culoareFundal)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = reper.denumire,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (esteCauzaBlocaj) Color(0xFFC62828) else Color(0xFF212529)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (esteCauzaBlocaj) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🛑 BLOCAJ ASAMBLARE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    } else if (esteBypassDirectLaAmbalaj) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE3F2FD), RoundedCornerShape(4.dp))
                                .border(0.8.dp, Color(0xFF1976D2), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("BYPASS ➔ DIRECT ATELIER 4 📦", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF0D47A1))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = "Necesar: ${reper.bucPerScaun} buc per scaun",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val totalDisponibilReper = reper.realizat + reper.stocPeRaft
                    Text(
                        text = "${(reper.procent * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = culoareStatus
                    )
                    Text(
                        text = "$totalDisponibilReper / ${reper.tintaTotala} buc",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "(Produs: ${reper.realizat} | Stoc: ${reper.stocPeRaft})",
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { reper.procent },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                trackColor = Color(0xFFE0E0E0),
                color = culoareStatus
            )

            // TRASABILITATE EXTINSĂ LA CLICK
            AnimatedVisibility(visible = esteSelectat) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text("🔍 TRASABILITATE & OCOLIRE SECTOARE:", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF0D6EFD))
                    Spacer(modifier = Modifier.height(2.dp))
                    if (esteBypassDirectLaAmbalaj) {
                        Text("• 🚀 REPER LIBER (FĂRĂ SUBANSAMBLU): Ocolește At. 2 (Montaj) și At. 3 (Vopsitorie). Merge direct din ${reper.atelier} la Atelierul 4 (Ambalaj final)!", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                    } else if (reper.subansambluDestinatie.isNotEmpty()) {
                        Text("• Montat în subansamblul: '${reper.subansambluDestinatie}' (Se asamblează în Atelierul 2)", fontSize = 10.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                    }
                    if (reper.fazaPrelucrare.isNotEmpty()) {
                        Text("• Fazele de lucru: ${reper.fazaPrelucrare}", fontSize = 10.sp, color = Color.DarkGray)
                    }
                    Text("• Stoc fizic intermediar disponibil pe raft: ${reper.stocPeRaft} buc", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (reper.stocPeRaft > 0) Color(0xFF198754) else Color(0xFF6C757D))
                    Text("• Rămas de produs pentru lot: ${(reper.tintaTotala - reper.realizat).coerceAtLeast(0)} buc", fontSize = 10.sp, color = Color.DarkGray)
                }
            }
        }
    }
}

// --- COMPONENTE GRAFICE PENTRU SCHEMA DE FLUX (FLOWCHART) ---

@Composable
fun BlocCapsulaStart(titlu: String, subtitlu: String, culoare: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .border(2.dp, culoare, RoundedCornerShape(30.dp)),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = culoare)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = titlu, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(text = subtitlu, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun BlocDecizieRomb(intrebare: String, daEticheta: String, estePozitiv: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .border(1.5.dp, Color(0xFF1976D2), RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🔷 $intrebare",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0D47A1),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .background(if (estePozitiv) Color(0xFF2E7D32) else Color(0xFF757575), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = daEticheta,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun SageataFluxCuText(eticheta: String) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = eticheta,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
        Canvas(modifier = Modifier.size(width = 16.dp, height = 20.dp)) {
            val startY = 0f
            val endY = size.height - 6.dp.toPx()
            val midX = size.width / 2

            drawLine(
                color = Color(0xFF1976D2),
                start = Offset(midX, startY),
                end = Offset(midX, endY),
                strokeWidth = 2.5.dp.toPx()
            )

            val path = Path().apply {
                moveTo(midX - 5.dp.toPx(), endY)
                lineTo(midX + 5.dp.toPx(), endY)
                lineTo(midX, size.height)
                close()
            }
            drawPath(path, color = Color(0xFF1976D2))
        }
    }
}

// --- GENERATOR RAPORT PROFESIONAL PDF A4 ---
fun genereazaSiDeschideRaportPdfA4(
    context: Context,
    listaComenzi: List<Map<String, Any>>,
    listaStocuriFaze: List<Map<String, Any>>,
    listaIstoric: List<Map<String, Any>>
) {
    try {
        val pdfDocument = PdfDocument()
        
        // Helper function pentru a parseza reper
        fun parseazaReper(nod: Any?, cantitateScauneTinta: Int, listaIstoricLocal: List<Map<String, Any>>, listaStocuriFazeLocal: List<Map<String, Any>>): ReperDetaliuTrasabilitate {
            val map = nod as? Map<*, *> ?: emptyMap<Any, Any>()
            val rId = map["reperId"]?.toString() ?: ""
            val denumire = map["denumirePiesa"]?.toString() ?: rId
            val buc = (map["cantitateNecesara"] as? Long)?.toInt() ?: 1
            val atelier = map["atelierAsociat"]?.toString() ?: "Atelier1"
            val faza = map["fazaPrelucrare"]?.toString() ?: ""
            val subansamblu = map["subansambluDestinatie"]?.toString() ?: ""

            val tintaTotala = cantitateScauneTinta * buc
            val realizat = listaIstoricLocal
                .filter { it["articolId"]?.toString() == rId && it["atelier"]?.toString() == atelier }
                .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }

            val idDocStoc = "${rId}_$atelier"
            val stocPeRaft = (listaStocuriFazeLocal.find { it["idDoc"] == idDocStoc }?.get("stocDisponibil") as? Long)?.toInt() ?: 0

            val disponibilTotal = realizat + stocPeRaft
            val procent = if (tintaTotala > 0) (disponibilTotal.toFloat() / tintaTotala).coerceIn(0f, 1f) else 0f

            return ReperDetaliuTrasabilitate(
                reperId = rId,
                denumire = denumire,
                bucPerScaun = buc,
                tintaTotala = tintaTotala,
                realizat = realizat,
                stocPeRaft = stocPeRaft,
                procent = procent,
                atelier = atelier,
                fazaPrelucrare = faza,
                subansambluDestinatie = subansamblu
            )
        }

        // Iterăm prin toate produsele
        var numarPagina = 1
        listaComenzi.distinctBy { it["modelId"]?.toString() }.forEach { comanda ->
            val modelId = comanda["modelId"]?.toString() ?: ""
            val modelNume = comanda["modelNume"]?.toString() ?: "Produs"
            val cantitateScauneTinta = (comanda["cantitateScaune"] as? Long)?.toInt() ?: 0
            val retetaBOM = (comanda["retetaBOM"] as? List<*>) ?: emptyList<Any>()
            
            val repereParsed = retetaBOM.map { parseazaReper(it, cantitateScauneTinta, listaIstoric, listaStocuriFaze) }

            // Format standard A4 la 72 DPI (595 x 842 puncte)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, numarPagina).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val paintText = Paint().apply { isAntiAlias = true; color = android.graphics.Color.DKGRAY; textSize = 9f }
            val paintTitlu = Paint().apply { isAntiAlias = true; color = android.graphics.Color.rgb(13, 71, 161); textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val paintHeaderSec = Paint().apply { isAntiAlias = true; color = android.graphics.Color.rgb(33, 37, 41); textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val paintTableHeader = Paint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE; textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val paintBg = Paint().apply { isAntiAlias = true }
            val paintLinie = Paint().apply { isAntiAlias = true; color = android.graphics.Color.LTGRAY; strokeWidth = 0.8f }

            var y = 40f

            // 1. HEADER DOCUMENT
            paintBg.color = android.graphics.Color.rgb(235, 243, 250)
            canvas.drawRect(30f, y - 15f, 565f, y + 50f, paintBg)
            canvas.drawText("RAPORT INDUSTRIAL DE PRODUCȚIE ȘI TRASABILITATE", 45f, y + 10f, paintTitlu)
            val dataFormatata = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            paintText.textSize = 9f
            canvas.drawText("Sistem Integrat GearsFabrică • Emis la: $dataFormatata", 45f, y + 30f, paintText)
            y += 75f

            // 2. SUMAR PRODUS & ȚINTĂ
            canvas.drawText("1. SUMAR PLAN DE PRODUCȚIE ACTIV", 35f, y, paintHeaderSec)
            y += 15f
            paintText.textSize = 10f
            canvas.drawText("• Produs comercial analizat: ${modelNume.uppercase()}", 45f, y, paintText)
            y += 14f
            canvas.drawText("• Țintă totală comandă lansată: $cantitateScauneTinta bucăți", 45f, y, paintText)
            y += 14f

            val acumMs = System.currentTimeMillis()
            val msSaptamana = 7L * 24 * 3600 * 1000
            val msLuna = 30L * 24 * 3600 * 1000

            val istoricModel = listaIstoric.filter { (it["articolId"]?.toString() == modelId || it["modelId"]?.toString() == modelId) }
            val prodSaptamana = istoricModel.filter { ((it["timestamp"] as? Long) ?: 0L) >= (acumMs - msSaptamana) }.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
            val prodLuna = istoricModel.filter { ((it["timestamp"] as? Long) ?: 0L) >= (acumMs - msLuna) }.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
            val prodTotalFinit = istoricModel.filter { it["atelier"]?.toString() == "Atelier4" }.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }

            canvas.drawText("• Realizat în ultima SĂPTĂMÂNĂ: $prodSaptamana buc  |  În ultima LUNĂ: $prodLuna buc", 45f, y, paintText)
            y += 14f
            val procFin = if (cantitateScauneTinta > 0) ((prodTotalFinit.toFloat() / cantitateScauneTinta) * 100).toInt() else 0
            canvas.drawText("• Total finalizat ambalat: $prodTotalFinit / $cantitateScauneTinta buc ($procFin%)", 45f, y, paintText)
            y += 25f

            // 3. TABEL DETALIAT REPERE ȘI SUBANSAMBLE
            canvas.drawText("2. SITUAȚIA COMPONENTELOR, STOCURILOR ȘI PROCENTELOR", 35f, y, paintHeaderSec)
            y += 15f

            // Header Tabel
            paintBg.color = android.graphics.Color.rgb(13, 71, 161)
            canvas.drawRect(35f, y - 10f, 560f, y + 10f, paintBg)
            canvas.drawText("Componentă / Reper", 40f, y + 3f, paintTableHeader)
            canvas.drawText("Secție", 200f, y + 3f, paintTableHeader)
            canvas.drawText("Țintă", 250f, y + 3f, paintTableHeader)
            canvas.drawText("Produs", 300f, y + 3f, paintTableHeader)
            canvas.drawText("Stoc Raft", 355f, y + 3f, paintTableHeader)
            canvas.drawText("Disponibil", 420f, y + 3f, paintTableHeader)
            canvas.drawText("Procent", 480f, y + 3f, paintTableHeader)
            canvas.drawText("Traseu", 525f, y + 3f, paintTableHeader)
            y += 20f

            var totalTintaPiese = 0
            var totalProdusPiese = 0
            var totalStocPiese = 0

            repereParsed.forEachIndexed { idx, p ->
                if (idx % 2 == 1) {
                    paintBg.color = android.graphics.Color.rgb(248, 249, 250)
                    canvas.drawRect(35f, y - 8f, 560f, y + 8f, paintBg)
                }
                val disp = p.realizat + p.stocPeRaft
                totalTintaPiese += p.tintaTotala
                totalProdusPiese += p.realizat
                totalStocPiese += p.stocPeRaft

                paintText.textSize = 8.5f
                val denumireTrunchiata = if (p.denumire.length > 25) p.denumire.take(24) + ".." else p.denumire
                canvas.drawText(denumireTrunchiata, 40f, y + 2f, paintText)
                canvas.drawText(p.atelier, 200f, y + 2f, paintText)
                canvas.drawText("${p.tintaTotala}", 250f, y + 2f, paintText)
                canvas.drawText("${p.realizat}", 300f, y + 2f, paintText)
                canvas.drawText("${p.stocPeRaft}", 355f, y + 2f, paintText)
                canvas.drawText("$disp", 420f, y + 2f, paintText)

                val procInt = (p.procent * 100).toInt()
                val paintProc = Paint(paintText).apply {
                    color = if (procInt >= 100) android.graphics.Color.rgb(46, 125, 50) else if (procInt > 0) android.graphics.Color.rgb(230, 81, 0) else android.graphics.Color.rgb(198, 40, 40)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("$procInt%", 480f, y + 2f, paintProc)

                val traseuText = if (p.subansambluDestinatie.isNotEmpty()) "Montaj" else "Direct At4"
                canvas.drawText(traseuText, 525f, y + 2f, paintText)

                canvas.drawLine(35f, y + 9f, 560f, y + 9f, paintLinie)
                y += 18f
            }

            // Rând Totaluri
            paintBg.color = android.graphics.Color.rgb(227, 242, 253)
            canvas.drawRect(35f, y - 8f, 560f, y + 12f, paintBg)
            val paintTotalBold = Paint().apply { isAntiAlias = true; color = android.graphics.Color.rgb(13, 71, 161); textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            canvas.drawText("TOTAL GENERAL COMPONENTE:", 40f, y + 3f, paintTotalBold)
            canvas.drawText("$totalTintaPiese", 250f, y + 3f, paintTotalBold)
            canvas.drawText("$totalProdusPiese", 300f, y + 3f, paintTotalBold)
            canvas.drawText("$totalStocPiese", 355f, y + 3f, paintTotalBold)
            canvas.drawText("${totalProdusPiese + totalStocPiese}", 420f, y + 3f, paintTotalBold)
            val procMediu = if (totalTintaPiese > 0) (((totalProdusPiese + totalStocPiese).toFloat() / totalTintaPiese) * 100).toInt() else 0
            canvas.drawText("$procMediu%", 480f, y + 3f, paintTotalBold)
            y += 30f

            // 4. TRASABILITATE ȘI RECOMANDĂRI GÂTUIRE
            canvas.drawText("3. ANALIZĂ DE TRASABILITATE ȘI BOTTLENECK", 35f, y, paintHeaderSec)
            y += 15f
            paintText.textSize = 9.5f
            val reperBlocant = repereParsed.filter { it.procent < 1f }.minByOrNull { it.procent }
            if (reperBlocant != null) {
                paintBg.color = android.graphics.Color.rgb(255, 235, 238)
                canvas.drawRect(35f, y - 10f, 560f, y + 32f, paintBg)
                val paintAlert = Paint().apply { isAntiAlias = true; color = android.graphics.Color.rgb(198, 40, 40); textSize = 9.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
                canvas.drawText("ATENȚIE - GÂTUIRE CRITICĂ IDENTIFICATĂ ÎN PRODUCȚIE:", 45f, y + 4f, paintAlert)
                canvas.drawText("Reperul '${reperBlocant.denumire}' din ${reperBlocant.atelier} are doar ${(reperBlocant.procent * 100).toInt()}% disponibil și blochează fluxul general!", 45f, y + 20f, paintText)
                y += 45f
            } else {
                canvas.drawText("• Toate componentele au stocul complet asigurat pentru acest plan.", 45f, y, paintText)
                y += 20f
            }

            // Semnătură / Footer
            y = 800f
            canvas.drawLine(35f, y, 560f, y, paintLinie)
            paintText.textSize = 8f
            canvas.drawText("Document generat automat din aplicația de gestiune industrială • Pagina $numarPagina / ${listaComenzi.distinctBy { it["modelId"]?.toString() }.size} (Format A4)", 35f, y + 14f, paintText)

            pdfDocument.finishPage(page)
            numarPagina++
        }

        // Salvare fișier PDF în cache-ul aplicației
        val numeFisier = "Raport_Toti_Produsele.pdf"
        val folderCache = File(context.cacheDir, "rapoarte").apply { if (!exists()) mkdirs() }
        val fisierDestinatie = File(folderCache, numeFisier)

        val outputStream = FileOutputStream(fisierDestinatie)
        pdfDocument.writeTo(outputStream)
        outputStream.close()
        pdfDocument.close()

        // Distribuire directă prin WhatsApp / Aplicații de mesagerie / Email
        val uriSecurizat = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fisierDestinatie)
        val intentiePartajare = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uriSecurizat)
            putExtra(Intent.EXTRA_SUBJECT, "Raport Producție & Trasabilitate - Toate Produsele")
            putExtra(Intent.EXTRA_TEXT, "Atașat raportul de producție și trasabilitate pentru toate produsele active.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intentiePartajare, "Trimite raportul pe WhatsApp sau altă aplicație..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Eroare la generare PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
