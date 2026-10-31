package com.example.sistem_gestiune_fabrica

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.sistem_gestiune_fabrica.screens.*
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import androidx.compose.ui.text.style.TextAlign

@Composable
fun CardSectorProgres(
    titluCurat: String,
    procent: Float,
    textStoc: String,
    areDreptApasare: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = areDreptApasare) { onClick() }
            .border(
                width = if (areDreptApasare) 1.5.dp else 0.dp,
                color = if (areDreptApasare) Color(0xFF2E7D32) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (areDreptApasare) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = titluCurat, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF212529))
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = textStoc, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = if (procent.isNaN()) 0f else procent,
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 7.dp,
                    trackColor = Color(0xFFE9ECEF),
                    color = if (areDreptApasare) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                )
                val afisajProcent = if (procent.isNaN()) 0 else (procent * 100).toInt()
                Text(text = "$afisajProcent%", fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (areDreptApasare) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val VERSIUNE_APP_COPTA_LOCAL = 3
    private var idDescarcareCurenta: Long = -1

    private val receptorFinalizareDescarcare = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val idId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (idId == idDescarcareCurenta && idId != -1L) {
                try {
                    val fisierApkSalvat = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GearsFabrica_Update.apk")
                    if (fisierApkSalvat.exists()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                            val intentieSetariInstalare = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intentieSetariInstalare)
                            return
                        }
                        val uriSecurizatCale = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fisierApkSalvat)
                        val intentieInstalareNativa = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uriSecurizatCale, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intentieInstalareNativa)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Actualizare descărcată! Verificați folderul Downloads.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 💡 METODĂ CURĂȚATĂ COMPLET: Fără bucle de înregistrare riscante care dau erori de analiză în editor!
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F9FA)) {
                    val context = LocalContext.current

                    // 🚀 ÎNREGISTRARE AGILĂ ÎN JETPACK COMPOSE: Se activează nativ prin contextul de randare local
                    DisposableEffect(context) {
                        val filtruAction = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                        try {
                            @SuppressLint("UnspecifiedRegisterReceiverFlag")
                            ContextCompat.registerReceiver(
                                context,
                                receptorFinalizareDescarcare,
                                filtruAction,
                                ContextCompat.RECEIVER_EXPORTED
                            )
                        } catch (e: Exception) {}

                        onDispose {
                            try {
                                context.unregisterReceiver(receptorFinalizareDescarcare)
                            } catch (e: Exception) {}
                        }
                    }

                    val prefs = remember { context.getSharedPreferences("GearsFabricaPrefs", Context.MODE_PRIVATE) }
                    var atelierTrimisLaRaportare by remember { mutableStateOf("Atelier1") }

                    val savedUser = prefs.getString("saved_user", "") ?: ""
                    val savedPass = prefs.getString("saved_pass", "") ?: ""
                    val isRemembered = prefs.getBoolean("remember_me", false)

                    var ecranCurent by remember { mutableStateOf(if (isRemembered && savedUser.isNotEmpty()) "PanouPrincipal" else "LoginScreen") }
                    var dateUtilizatorLogat by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }

                    var comenziActive by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
                    var listaStocuriFaze by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
                    var istoricProductieTotal by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
                    var tranzactiiCerneluri by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

                    var idModelSelectatEcran by remember { mutableStateOf("") }

                    var utilizatorText by remember { mutableStateOf(savedUser) }
                    var parolaText by remember { mutableStateOf(savedPass) }
                    var tineMinteBifa by remember { mutableStateOf(isRemembered) }

                    var versiuneNouaDisponibilaDeFirebase by remember { mutableStateOf(false) }
                    var urlLinkDownloadUpdate by remember { mutableStateOf("") }

                    LaunchedEffect(Unit) {
                        firestore.collection("setari_sistem").document("versiune_app")
                            .addSnapshotListener { snapshot, _ ->
                                if (snapshot != null && snapshot.exists()) {
                                    val versiuneStringCloud = snapshot.getString("numar_versiune") ?: "2"
                                    val versiuneServerCloud = versiuneStringCloud.toIntOrNull() ?: VERSIUNE_APP_COPTA_LOCAL
                                    val linkServer = snapshot.getString("url_download") ?: ""

                                    versiuneNouaDisponibilaDeFirebase = versiuneServerCloud > VERSIUNE_APP_COPTA_LOCAL
                                    urlLinkDownloadUpdate = linkServer
                                }
                            }
                    }

                    LaunchedEffect(Unit) {
                        if (isRemembered && savedUser.isNotEmpty()) {
                            firestore.collection("utilizatori_sistem").whereEqualTo("username", savedUser).get()
                                .addOnSuccessListener { snapshot ->
                                    val doc = snapshot.documents.firstOrNull()
                                    if (doc != null && doc.getString("pin") == savedPass) {
                                        dateUtilizatorLogat = doc.data ?: emptyMap()
                                    } else {
                                        firestore.collection("utilizatori").whereEqualTo("username", savedUser).get()
                                            .addOnSuccessListener { snap2 ->
                                                val doc2 = snap2.documents.firstOrNull()
                                                if (doc2 != null && doc2.getString("pin") == savedPass) {
                                                    dateUtilizatorLogat = doc2.data ?: emptyMap()
                                                }
                                            }
                                    }
                                }
                        }
                    }
                    LaunchedEffect(Unit) {
                        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
                            if (snapshot != null) {
                                val list = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
                                comenziActive = list
                                val planuriInLucru = list.filter { it["stare"]?.toString() == "In Lucru" }
                                if (idModelSelectatEcran.isEmpty() && planuriInLucru.isNotEmpty()) {
                                    idModelSelectatEcran = planuriInLucru.first()["modelId"]?.toString() ?: ""
                                }
                            }
                        }
                        firestore.collection("stocuri_faze").addSnapshotListener { snapshot, _ ->
                            if (snapshot != null) listaStocuriFaze = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDocStoc" to it.id) }
                        }
                        firestore.collection("istoric_productie").addSnapshotListener { snapshot, _ ->
                            if (snapshot != null) istoricProductieTotal = snapshot.documents.map { it.data ?: emptyMap() }
                        }
                        firestore.collection("tranzactii_cerneluri")
                            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .addSnapshotListener { snapshot, _ ->
                                if (snapshot != null) {
                                    tranzactiiCerneluri = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
                                }
                            }
                    }
                    val loturiActiveInLucru = comenziActive.filter { it["stare"]?.toString() == "In Lucru" }
                    val comandaVizualizataAcum = loturiActiveInLucru.find { it["modelId"]?.toString() == idModelSelectatEcran } ?: loturiActiveInLucru.firstOrNull()
                    fun calculeazaDateAtelier(atelierCod: String): Pair<Float, String> {
                        val planRef = comandaVizualizataAcum ?: return Pair(0f, "0 / 0 buc")
                        val cantitateScauneTinta = (planRef["cantitateScaune"] as? Long)?.toInt() ?: 0
                        val modelIdScaun = planRef["modelId"]?.toString() ?: ""
                        val timestampLansareComanda = (planRef["timestampLansare"] as? Long) ?: 0L
                        if (cantitateScauneTinta <= 0 || modelIdScaun.isEmpty()) return Pair(0f, "0 / 0 buc")
                        if (atelierCod == "Atelier4") {
                            val realizatScauneIstoric = istoricProductieTotal
                                .filter { document ->
                                    val matchAtelier = document["atelier"]?.toString() == "Atelier4"
                                    val idArticol = document["articolId"]?.toString() ?: ""
                                    val idModel = document["modelId"]?.toString() ?: ""
                                    val ts = (document["timestamp"] as? Long) ?: 0L
                                    matchAtelier && (idArticol == modelIdScaun || idModel == modelIdScaun) && ts >= timestampLansareComanda
                                }
                                .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
                            val pro = (realizatScauneIstoric.toFloat() / cantitateScauneTinta).coerceIn(0f, 1f)
                            return Pair(pro, "$realizatScauneIstoric / $cantitateScauneTinta buc ambalate complet")
                        }
                        val retetaBOMAList = planRef["retetaBOM"] as? List<*> ?: emptyList<Any>()

                        var totalTintaPieseInitiala = 0
                        var totalRaportatPieseAtelierPlatit = 0
                        var aGasitPieseInReteta = false
                        for (obiectInReteta in retetaBOMAList) {
                            val comp = obiectInReteta as? Map<*, *> ?: continue
                            val sectorAsociat = (comp["atelierAsociat"]?.toString() ?: "").trim()
                            val apartineAcestuiAtelier = if (atelierCod == "Atelier1") {
                                sectorAsociat.equals("Atelier1", ignoreCase = true) || sectorAsociat.isEmpty()
                            } else if (atelierCod == "Atelier0") {
                                // Toleranță la cifra '0' vs litera 'O'
                                sectorAsociat.equals("Atelier0", ignoreCase = true) || sectorAsociat.equals("AtelierO", ignoreCase = true)
                            } else {
                                sectorAsociat.equals(atelierCod, ignoreCase = true)
                            }
                            if (apartineAcestuiAtelier) {
                                val rId = comp["reperId"]?.toString() ?: ""
                                val deConsumatPerScaun = (comp["cantitateNecesara"] as? Long)?.toInt() ?: 1
                                if (rId.isNotEmpty()) {
                                    aGasitPieseInReteta = true
                                    val tintaReper = cantitateScauneTinta * deConsumatPerScaun
                                    totalTintaPieseInitiala += tintaReper
                                    val raportatPerReper = istoricProductieTotal
                                        .filter { document ->
                                            val sct = (document["atelier"]?.toString() ?: "").trim()
                                            val idArt = document["articolId"]?.toString() ?: ""
                                            val ts = (document["timestamp"] as? Long) ?: 0L
                                            val sctMatch = if (atelierCod == "Atelier0") {
                                                sct.equals("Atelier0", ignoreCase = true) || sct.equals("AtelierO", ignoreCase = true)
                                            } else {
                                                sct.equals(atelierCod, ignoreCase = true)
                                            }
                                            sctMatch && idArt == rId && ts >= timestampLansareComanda
                                        }
                                        .sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
                                    // Dacă s-a declarat peste planul țintă, se ia maxim ținta pentru procentaj corect
                                    totalRaportatPieseAtelierPlatit += raportatPerReper.coerceAtMost(tintaReper)
                                }
                            }
                        }
                        if (!aGasitPieseInReteta) {
                            return Pair(0f, "Fără piese în rețetă")
                        }
                        val pro = if (totalTintaPieseInitiala > 0) (totalRaportatPieseAtelierPlatit.toFloat() / totalTintaPieseInitiala).coerceIn(0f, 1f) else 0f
                        return Pair(pro, "$totalRaportatPieseAtelierPlatit / $totalTintaPieseInitiala buc")
                    }
                    val (procentScaune, textScaune, modelNume) = if (comandaVizualizataAcum != null) {
                        val mId = comandaVizualizataAcum["modelId"]?.toString() ?: ""
                        val mNumeText = comandaVizualizataAcum["modelNume"]?.toString() ?: "Scaun"
                        val tinta = (comandaVizualizataAcum["cantitateScaune"] as? Long)?.toInt() ?: 0
                        val tsLansare = (comandaVizualizataAcum["timestampLansare"] as? Long) ?: 0L
                        val realizatScauneIstoric = istoricProductieTotal.filter {
                            (it["articolId"]?.toString() == mId || it["modelId"]?.toString() == mId) &&
                            it["atelier"]?.toString() == "Atelier4" &&
                            ((it["timestamp"] as? Long) ?: 0L) >= tsLansare
                        }.sumOf { (it["cantitateOk"] as? Long)?.toInt() ?: 0 }
                        val realizatLimitat = realizatScauneIstoric.coerceAtMost(tinta)
                        val pro = if (tinta > 0) (realizatLimitat.toFloat() / tinta).coerceIn(0f, 1f) else 0f
                        Triple(pro, "$realizatScauneIstoric / $tinta buc ambalate complet", mNumeText)
                    } else {
                        Triple(0f, "Niciun plan în execuție curent", "FĂRĂ PLAN ACTIV")
                    }
                    val (proAt0, stocAt0) = calculeazaDateAtelier("Atelier0")
                    val (proAt1, stocAt1) = calculeazaDateAtelier("Atelier1")
                    val (proAt2, stocAt2) = calculeazaDateAtelier("Atelier2")
                    val (proAt3, stocAt3) = calculeazaDateAtelier("Atelier3")
                    val (proAt4, stocAt4) = calculeazaDateAtelier("Atelier4")
                    val drepturiObiectBrut = dateUtilizatorLogat["drepturiFerestre"]
                    val drepturiFerestre = if (drepturiObiectBrut is Map<*, *>) { drepturiObiectBrut.mapKeys { it.key.toString() }.mapValues { it.value } } else emptyMap<String, Any>()
                    val areDreptStocuri = drepturiFerestre["stocuri"] as? Boolean ?: false
                    val areDreptAdmin = drepturiFerestre["administrare_drepturi"] as? Boolean ?: false
                    val areDreptPlanuri = drepturiFerestre["lansare_planuri"] as? Boolean ?: false
                    val areDreptNomenclator = drepturiFerestre["retete_nomenclator"] as? Boolean ?: false
                    val areDreptRapoarte = drepturiFerestre["rapoarte_analiza"] as? Boolean ?: false
                    val areDreptGatuiri = drepturiFerestre["dashboard"] == true || drepturiFerestre["dashboard"]?.toString() == "true" || drepturiFerestre["detector_gatuiri"] == true || drepturiFerestre["detector_gatuiri"]?.toString() == "true"
                    val areDreptCerneluri = drepturiFerestre["gestiune_cerneluri"] as? Boolean ?: false
                    
                    // Calculare stoc cerneluri
                    val culoriDisponibile = listOf("Cyan", "Magenta", "Yellow", "Black")
                    val stocuriCerneluri = remember(tranzactiiCerneluri) {
                        culoriDisponibile.associateWith { culoare ->
                            tranzactiiCerneluri
                                .filter { it["culoare"]?.toString() == culoare }
                                .sumOf { 
                                    val tip = it["tip"]?.toString() ?: ""
                                    val cantitate = (it["cantitate"] as? Long)?.toInt() ?: 0
                                    if (tip == "intrare") cantitate else -cantitate
                                }
                        }
                    }
                    
                    // Verificare stocuri sub 20 unități
                    val culoriCuStocMic = remember(stocuriCerneluri) {
                        stocuriCerneluri.filter { it.value in 1..19 }.keys.toList()
                    }
                    when (ecranCurent) {
                        "LoginScreen" -> {
                            Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SISTEM INTEGRAT FABRICĂ", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Text(text = "Versiune curentă sistem v$VERSIUNE_APP_COPTA_LOCAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                if (versiuneNouaDisponibilaDeFirebase) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            if (urlLinkDownloadUpdate.isNotEmpty()) {
                                                try {
                                                    val fisierVechiStergere = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GearsFabrica_Update.apk")
                                                    if (fisierVechiStergere.exists()) fisierVechiStergere.delete()
                                                } catch(e: Exception) {}
                                                try {
                                                    val managerDescarcari = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                    val adresaUriSursa = Uri.parse(urlLinkDownloadUpdate.trim())
                                                    val cerereDescarcare = DownloadManager.Request(adresaUriSursa).apply {
                                                        setTitle("Actualizare Gestiune Fabrică")
                                                        setDescription("Se descarcă pachetul APK...")
                                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GearsFabrica_Update.apk")
                                                        setMimeType("application/vnd.android.package-archive")
                                                    }
                                                    idDescarcareCurenta = managerDescarcari.enqueue(cerereDescarcare)
                                                    Toast.makeText(context, "Descărcarea update-ului a pornit în fundal! 📥", Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    try {
                                                        val intentieUrgenta = Intent(Intent.ACTION_VIEW, Uri.parse(urlLinkDownloadUpdate.trim())).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                        context.startActivity(intentieUrgenta)
                                                    } catch (err: Exception) {
                                                        Toast.makeText(context, "Eroare rețea fabrică!", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("📥 VERSIUNE NOUĂ DISPONIBILĂ! APĂSAȚI PENTRU UPDATE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                OutlinedTextField(value = utilizatorText, onValueChange = { utilizatorText = it }, label = { Text("Utilizator") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(value = parolaText, onValueChange = { parolaText = it }, label = { Text("Cod PIN") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = tineMinteBifa, onCheckedChange = { tineMinteBifa = it })
                                    Text("Ține-mă minte pe această tabletă", fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val uIn = utilizatorText.trim()
                                        val pIn = parolaText.trim()
                                        if (uIn.isEmpty() || pIn.isEmpty()) return@Button
                                        firestore.collection("utilizatori_sistem").whereEqualTo("username", uIn).get().addOnSuccessListener { snap ->
                                            val d = snap.documents.firstOrNull()
                                            if (d != null && d.getString("pin") == pIn) {
                                                dateUtilizatorLogat = d.data ?: emptyMap()
                                                val ed = prefs.edit()
                                                if (tineMinteBifa) { ed.putString("saved_user", uIn); ed.putString("saved_pass", pIn); ed.putBoolean("remember_me", true) } else { ed.clear() }
                                                ed.apply()
                                                ecranCurent = "PanouPrincipal"
                                            } else {
                                                firestore.collection("utilizatori").whereEqualTo("username", uIn).get().addOnSuccessListener { snap2 ->
                                                    val d2 = snap2.documents.firstOrNull()
                                                    if (d2 != null && d2.getString("pin") == pIn) {
                                                        dateUtilizatorLogat = d2.data ?: emptyMap()
                                                        val ed = prefs.edit()
                                                        if (tineMinteBifa) { ed.putString("saved_user", uIn); ed.putString("saved_pass", pIn); ed.putBoolean("remember_me", true) } else { ed.clear() }
                                                        ed.apply()
                                                        ecranCurent = "PanouPrincipal"
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) { Text("AUTENTIFICARE SYSTEM 🔐", fontWeight = FontWeight.Bold) }
                            }
                        }
                        "PanouPrincipal" -> {
                            Scaffold(
                                bottomBar = {
                                    Box(modifier = Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color.LightGray.copy(alpha = 0.3f)).padding(16.dp), contentAlignment = Alignment.Center) {
                                        Button(
                                            onClick = {
                                                dateUtilizatorLogat = emptyMap()
                                                val ed = prefs.edit()
                                                ed.clear()
                                                ed.apply()
                                                utilizatorText = ""
                                                parolaText = ""
                                                idModelSelectatEcran = ""
                                                ecranCurent = "LoginScreen"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().height(46.dp)
                                        ) { Text("DECONECTARE / SCHIMBĂ OPERATORUL 🚪", fontWeight = FontWeight.Black, fontSize = 14.sp) }
                                    }
                                }
                            ) { innerPadding ->
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    item {
                                        Text(text = "MONITORIZARE FABRICĂ LIVE", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF0D6EFD))
                                        val numeUser = dateUtilizatorLogat["nume"]?.toString() ?: "Utilizator"
                                        val functieUser = dateUtilizatorLogat["functie"]?.toString() ?: "Sector"
                                        Text(text = "Operator: $numeUser ($functieUser) | Panou Securizat", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    
                                    // Atenționare stocuri cerneluri sub 20 unități
                                    if (areDreptCerneluri && culoriCuStocMic.isNotEmpty()) {
                                        item {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFC107))
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = "⚠️ ATENȚIE: STOCURI CERNELURI SCĂZUTE",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF856404)
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "Următoarele culori au stoc sub 20 unități: ${culoriCuStocMic.joinToString(", ")}",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF856404)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (versiuneNouaDisponibilaDeFirebase) {
                                        item {
                                            Button(
                                                onClick = {
                                                    if (urlLinkDownloadUpdate.isNotEmpty()) {
                                                        try {
                                                            val fisierVechiStergere = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GearsFabrica_Update.apk")
                                                            if (fisierVechiStergere.exists()) fisierVechiStergere.delete()
                                                        } catch(e: Exception) {}
                                                        try {
                                                            val managerDescarcari = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                            val adresaUriSursa = Uri.parse(urlLinkDownloadUpdate.trim())
                                                            val cerereDescarcare = DownloadManager.Request(adresaUriSursa).apply {
                                                                setTitle("Actualizare Gestiune Fabrică")
                                                                setDescription("Se descarcă pachetul APK...")
                                                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GearsFabrica_Update.apk")
                                                                setMimeType("application/vnd.android.package-archive")
                                                            }
                                                            idDescarcareCurenta = managerDescarcari.enqueue(cerereDescarcare)
                                                            Toast.makeText(context, "Descărcarea update-ului a pornit în fundal! 📥", Toast.LENGTH_LONG).show()
                                                        } catch (e: Exception) {
                                                            try {
                                                                val intentieUrgenta = Intent(Intent.ACTION_VIEW, Uri.parse(urlLinkDownloadUpdate.trim())).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                                context.startActivity(intentieUrgenta)
                                                            } catch (err: Exception) {
                                                                Toast.makeText(context, "Eroare rețea fabrică!", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(48.dp)
                                            ) {
                                                Text("⚠️ ACTUALIZARE TELEFON/TABLETĂ DISPONIBILĂ 📥", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                    item {
                                        // 💡 Schimbat Row cu FlowRow configurat pe fix 2 coloane
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            maxItemsInEachRow = 2 // Forțează maximum 2 elemente pe un rând
                                        ) {
                                            loturiActiveInLucru.forEach { lot ->
                                                val lotNume = lot["modelNume"]?.toString() ?: "Scaun"
                                                val lotId = lot["modelId"]?.toString() ?: ""

                                                // Învelim în Box cu weight(1f) pentru ca ambele coloane să fie perfect egale (50% - 50%)
                                                Box(modifier = Modifier.weight(1f)) {
                                                    FilterChip(
                                                        selected = idModelSelectatEcran == lotId,
                                                        onClick = { idModelSelectatEcran = lotId },
                                                        label = {
                                                            Text(
                                                                lotNume.uppercase(), // 💡 SOLUȚIE: Am șters "text =" și am lăsat valoarea directă
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Black,
                                                                textAlign = TextAlign.Center,
                                                                modifier = Modifier.fillMaxWidth()
                                                            )

                                                        },
                                                        modifier = Modifier.fillMaxWidth() // Întinde filtrul pe toată coloana lui
                                                    )
                                                }
                                            }

                                            // Truc vizual: Dacă numărul total de loturi este impar, adăugăm un spațiu
                                            // gol ca ultimul buton să nu se lăbească pe tot ecranul tabletei
                                            if (loturiActiveInLucru.size % 2 != 0) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }

                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
                                        ) {
                                            Row(modifier = Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = "PRODUS AFISAT ACUM LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                    Text(text = modelNume.uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(text = textScaune, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF455A64))
                                                }
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                                                    CircularProgressIndicator(progress = procentScaune, modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp, trackColor = Color.White.copy(alpha = 0.5f), color = Color(0xFF2E7D32))
                                                    Text(text = "${(procentScaune * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
                                                }
                                            }
                                        }
                                    }
                                    val drepturiSectoareBrut = dateUtilizatorLogat["drepturiAteliere"]
                                    val drepturiAteliereUtilizator = if (drepturiSectoareBrut is Map<*, *>) { drepturiSectoareBrut.mapKeys { it.key.toString() }.mapValues { it.value } } else emptyMap<String, Any>()
                                    val functieUserText = dateUtilizatorLogat["functie"]?.toString() ?: "Operator"
                                    val esteSefSauAdmin = functieUserText.uppercase().contains("ADMIN") || functieUserText.uppercase().contains("MAISTRU")
                                    val accesAt0 = esteSefSauAdmin || (drepturiAteliereUtilizator["Atelier0"] == true || drepturiAteliereUtilizator["Atelier0"]?.toString() == "true")
                                    val accesAt1 = esteSefSauAdmin || (drepturiAteliereUtilizator["Atelier1"] == true || drepturiAteliereUtilizator["Atelier1"]?.toString() == "true")
                                    val accesAt2 = esteSefSauAdmin || (drepturiAteliereUtilizator["Atelier2"] == true || drepturiAteliereUtilizator["Atelier2"]?.toString() == "true")
                                    val accesAt3 = esteSefSauAdmin || (drepturiAteliereUtilizator["Atelier3"] == true || drepturiAteliereUtilizator["Atelier3"]?.toString() == "true")
                                    val accesAt4 = esteSefSauAdmin || (drepturiAteliereUtilizator["Atelier4"] == true || drepturiAteliereUtilizator["Atelier4"]?.toString() == "true")
                                    item { CardSectorProgres("ATELIERUL 0 (Prelucrare) ⚙️", proAt0, stocAt0, accesAt0) { atelierTrimisLaRaportare = "Atelier0"; ecranCurent = "RaportareProductie" } }
                                    item { CardSectorProgres("ATELIERUL 1 (Digital print) \uD83D\uDDA8\uFE0F", proAt1, stocAt1, accesAt1) { atelierTrimisLaRaportare = "Atelier1"; ecranCurent = "RaportareProductie" } }
                                    item { CardSectorProgres("ATELIERUL 2 (Montaj) 🔨", proAt2, stocAt2, accesAt2) { atelierTrimisLaRaportare = "Atelier2"; ecranCurent = "RaportareProductie" } }
                                    item { CardSectorProgres("ATELIERUL 3 (Finisaj & vopsitorie) 🎨", proAt3, stocAt3, accesAt3) { atelierTrimisLaRaportare = "Atelier3"; ecranCurent = "RaportareProductie" } }
                                    item { CardSectorProgres("ATELIERUL 4 (Tapiterie & Ambalaj) 📦", proAt4, stocAt4, accesAt4) { atelierTrimisLaRaportare = "Atelier4"; ecranCurent = "RaportareProductie" } }
                                    item { Spacer(modifier = Modifier.height(4.dp)); Divider(color = Color.LightGray.copy(alpha = 0.4f)) }
                                    
                                    // Buton Tutorial - disponibil pentru toți utilizatorii
                                    item {
                                        Button(
                                            onClick = { ecranCurent = "Tutorial" },
                                            modifier = Modifier.fillMaxWidth().height(46.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("📚 INSTRUCȚIUNI UTILIZARE", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    
                                    if (areDreptStocuri) {
                                        item { Button(onClick = { ecranCurent = "MonitorizareStocuri" }, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("MONITORIZARE GLOBALĂ STOCURI 🪵", fontWeight = FontWeight.Bold) } }
                                    }
                                    if (esteSefSauAdmin) {
                                        item { Button(onClick = { ecranCurent = "SarciniMaistri" }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) { Text("📋 SARCINI MAISTRI (ORGANIZARE)", fontWeight = FontWeight.Bold) } }

                                    }
                                    if (areDreptNomenclator) {
                                        item { Button(onClick = { ecranCurent = "NomenclatorBOM" }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("NOMENCLATOR REȚETE (BOM) 📋", fontWeight = FontWeight.Bold) } }
                                        item { Button(onClick = { ecranCurent = "GraficNormeConsum" }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F))) { Text("📊 GRAFIC NORME CONSUM", fontWeight = FontWeight.Bold) } }
                                    }
                                    if (areDreptPlanuri) {
                                        item { Button(onClick = { ecranCurent = "LansarePlan" }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("LANSARE LOGISTICĂ PLANURI 🚀", fontWeight = FontWeight.Bold) } }
                                    }
                                    if (areDreptGatuiri || areDreptAdmin) {
                                        item { Button(onClick = { ecranCurent = "CapacitateProductie" }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))) { Text("🔍 ANALIZĂ PREDICTIVĂ", fontWeight = FontWeight.Bold) } }
                                    }
                                    if (areDreptAdmin) {
                                        item { Button(onClick = { ecranCurent = "AdministrareDrepturi" }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) { Text("⚙️ REGLARE DREPTURI OPERATORI", fontWeight = FontWeight.Bold) } }
                                    }
                                    if (areDreptRapoarte) {
                                        item { Button(onClick = { ecranCurent = "RapoartareSaptamanaLuna" }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F))) { Text("📊 RAPOARTE PRODUCȚIE", fontWeight = FontWeight.Bold) } }
                                    }

                                    // Buton Nou: Gestiune & Rapoarte Consumabile (Aracet, Lac, Feronerie)
                                    item {
                                        Button(
                                            onClick = { ecranCurent = "GestiuneConsumabile" },
                                            modifier = Modifier.fillMaxWidth().height(46.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("🧪 CONSUMABILE & AUXILIARE (STOC & RAPORT)", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }

                                    // Fișa zilnică este disponibilă oricărui utilizator logat (își vede propria fișă, iar adminul îi vede pe toți)
                                    item {
                                        Button(
                                            onClick = { ecranCurent = "FisaZilnicaOperatori" },
                                            modifier = Modifier.fillMaxWidth().height(46.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(if (esteSefSauAdmin || areDreptAdmin) "📑 FIȘĂ TOTALIZATĂ ZILNICĂ (OPERATORI)" else "📑 FIȘA MEA ZILNICĂ", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }

                                    // 📦 1. REPARAT RECONECTARE RUTĂ: Folosește ruta originală a ecranului tău de stocuri
                                    item {
                                        Button(
                                            onClick = { ecranCurent = "Inventariere" }, // 💡 Corectat înapoi la denumirea ta originală din proiect
                                            modifier = Modifier.fillMaxWidth().height(46.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                                            shape = RoundedCornerShape(24.dp)
                                        ) {
                                            Text("📦 INVENTARIERE STOCURI", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }

                                    // 🔍 2. BUTONUL DE AUDIT ADĂUGAT PERFECT CORELAT



                                    // 🔍 2. NOUL BUTON DE AUDIT ADĂUGAT PERFECT ÎN CORECTITUDINE CU LISTA STRUCTURATĂ PLIMOB
                                    // ⚙️ REGLARE ADMINISTRATOR: Doar conturile cu drept de Admin pot accesa Auditul de Stocuri
                                    if (areDreptAdmin) {
                                        item {
                                            Button(
                                                onClick = { ecranCurent = "AUDIT_STOCURI" },
                                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                                                shape = RoundedCornerShape(24.dp)
                                            ) {
                                                Text(
                                                    text = "🔍 AUDIT STOCURI (FRAGMENTATE)",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        item {
                                            Button(
                                                onClick = { ecranCurent = "AUDIT_NOMENCLATOR" },
                                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4527A0)),
                                                shape = RoundedCornerShape(24.dp)
                                            ) {
                                                Text(
                                                    text = "📋 AUDIT NOMENCLATOR & BOM (ADMIN)",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }





                                    if (areDreptCerneluri) {
                                        item {
                                            Button(
                                                onClick = { ecranCurent = "GestiuneCerneluri" },
                                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("🎨 GESTIUNE CERNELURI IMPRIMARE", fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                    item {
                                        Button(
                                            onClick = { ecranCurent = "TrasabilitateArbore" },
                                            modifier = Modifier.fillMaxWidth().height(46.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                                        ) {
                                            Text("🌳 TRASABILITATE ȘI ARBORE PRODUS", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        "RaportareProductie" -> RaportareProductieScreen(firestore = firestore, userData = dateUtilizatorLogat, atelierInitialCod = atelierTrimisLaRaportare, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "SarciniMaistri" -> SarciniMaistriScreen(firestore = firestore, userData = dateUtilizatorLogat, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "Inventariere" -> InventariereScreen(firestore = firestore, userData = dateUtilizatorLogat, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "CapacitateProductie", "DetectorGatuiri" -> MonitorizareCapacitateScreen(firestore = firestore, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "NomenclatorBOM" -> NomenclatorBomScreen(firestore = firestore, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "LansarePlan" -> LansarePlanScreen(firestore = firestore, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "MonitorizareStocuri" -> GestiuneStocuriScreen(firestore = firestore, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "AdministrareDrepturi" -> DrepturiUtilizatoriScreen(firestore = firestore, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "RapoartareSaptamanaLuna" -> RaportIstoricScreen(firestore = firestore, userData = dateUtilizatorLogat, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "FisaZilnicaOperatori" -> FisaZilnicaOperatoriScreen(firestore = firestore, userData = dateUtilizatorLogat, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "TrasabilitateArbore" -> TrasabilitateArboreScreen(firestore = firestore, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "Tutorial" -> TutorialScreen(userData = dateUtilizatorLogat, onBackClick = { ecranCurent = "PanouPrincipal" })
                        "AUDIT_STOCURI" -> AuditStocuriSaptamanalScreen(
                            firestore = firestore,
                            onBackClick = { ecranCurent = "PanouPrincipal" } // 💡 Sfat tehnic: Trimite-l înapoi la ecranul de stocuri ("Inventariere") sau direct la "MENIU", în funcție de unde ai pornit!
                        )
                        "AUDIT_NOMENCLATOR" -> AuditNomenclatorScreen(
                            firestore = firestore,
                            onBackClick = { ecranCurent = "PanouPrincipal" }
                        )
                        "GestiuneCerneluri" -> GestiuneCerneluriScreen(
                            firestore = firestore,
                            userData = dateUtilizatorLogat,
                            onBackClick = { ecranCurent = "PanouPrincipal" }
                        )
                        "GraficNormeConsum" -> GraficNormeConsumScreen(
                            firestore = firestore,
                            onBackClick = { ecranCurent = "PanouPrincipal" }
                        )
                        "GestiuneConsumabile" -> GestiuneConsumabileScreen(
                            firestore = firestore,
                            userData = dateUtilizatorLogat,
                            onBackClick = { ecranCurent = "PanouPrincipal" }
                        )


                    }
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
    }
}