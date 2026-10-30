package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

data class TranzactieCerneala(
    val id: String,
    val tip: String, // "intrare" sau "iesire"
    val culoare: String, // "Yellow", "Magenta", "Cyan", "Black"
    val cantitate: Int,
    val operator: String,
    val timestamp: Long,
    val observatii: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestiuneCerneluriScreen(
    firestore: FirebaseFirestore,
    userData: Map<String, Any>,
    onBackClick: () -> Unit
) {
    val usernameCurent = userData["username"]?.toString() ?: "unknown"
    val numeUser = userData["nume"]?.toString() ?: usernameCurent
    
    // Verificare permisiune
    val drepturiObiectBrut = userData["drepturiFerestre"]
    val drepturiFerestre = if (drepturiObiectBrut is Map<*, *>) {
        drepturiObiectBrut.mapKeys { it.key.toString() }.mapValues { it.value }
    } else emptyMap<String, Any>()
    val areDreptCerneluri = drepturiFerestre["gestiune_cerneluri"] as? Boolean ?: false
    
    // Verificare dacă este admin sau maistru
    val functieUserText = userData["functie"]?.toString() ?: "Operator"
    val esteSefSauAdmin = functieUserText.uppercase().contains("ADMIN") || functieUserText.uppercase().contains("MAISTRU")
    
    // Acces permis doar dacă are drept specific sau este admin/maistru
    val areAcces = areDreptCerneluri || esteSefSauAdmin
    
    // Dacă nu are acces, afișează mesaj și buton de întoarcere
    if (!areAcces) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⛔ ACCES INTERZIS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFDC3545)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Nu aveți permisiunea de a accesa această funcționalitate.",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("ÎNAPOI", fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    
    var istoricTranzactii by remember { mutableStateOf<List<TranzactieCerneala>>(emptyList()) }
    
    var mesajStatus by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(false) }
    
    // State pentru cantitățile introduse pentru fiecare culoare
    var cantitateCyan by remember { mutableStateOf("") }
    var cantitateMagenta by remember { mutableStateOf("") }
    var cantitateYellow by remember { mutableStateOf("") }
    var cantitateBlack by remember { mutableStateOf("") }
    
    // Stoc maxim pentru barele de progres
    val stocMaxim = 100
    
    // Culorile disponibile
    val culoriDisponibile = listOf("Cyan", "Magenta", "Yellow", "Black")
    
    // Funcție pentru înregistrare tranzacție
    fun inregistreazaTranzactie(culoare: String, cantitate: Int, tip: String) {
        if (cantitate <= 0) {
            mesajStatus = "Introduceți o cantitate validă!"
            return
        }
        seIncarca = true
        val tranzactie = mapOf(
            "tip" to tip,
            "culoare" to culoare,
            "cantitate" to cantitate,
            "operator" to usernameCurent,
            "timestamp" to System.currentTimeMillis(),
            "observatii" to ""
        )
        
        firestore.collection("tranzactii_cerneluri")
            .add(tranzactie)
            .addOnSuccessListener {
                seIncarca = false
                when(culoare) {
                    "Cyan" -> cantitateCyan = ""
                    "Magenta" -> cantitateMagenta = ""
                    "Yellow" -> cantitateYellow = ""
                    "Black" -> cantitateBlack = ""
                }
                mesajStatus = "${if (tip == "intrare") "Intrare" else "Ieșire"} de $cantitate unități $culoare înregistrată cu succes!"
            }
            .addOnFailureListener {
                seIncarca = false
                mesajStatus = "Eroare la înregistrare!"
            }
    }
    
    LaunchedEffect(Unit) {
        firestore.collection("tranzactii_cerneluri")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    istoricTranzactii = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data
                        TranzactieCerneala(
                            id = doc.id,
                            tip = data?.get("tip")?.toString() ?: "",
                            culoare = data?.get("culoare")?.toString() ?: "",
                            cantitate = (data?.get("cantitate") as? Long)?.toInt() ?: 0,
                            operator = data?.get("operator")?.toString() ?: "",
                            timestamp = data?.get("timestamp") as? Long ?: 0L,
                            observatii = data?.get("observatii")?.toString() ?: ""
                        )
                    }
                }
            }
    }
    
    // Calculare stoc curent per culoare
    val stocuriCurent = remember(istoricTranzactii) {
        culoriDisponibile.associateWith { culoare ->
            istoricTranzactii
                .filter { it.culoare == culoare }
                .sumOf { if (it.tip == "intrare") it.cantitate else -it.cantitate }
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // --- ZONĂ FIXĂ ---
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
                    Text("Gestiune Cerneluri Imprimare", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Operator: $numeUser", fontSize = 11.sp, color = Color.Gray)
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
                Text(
                    text = mesajStatus,
                    color = Color(0xFF198754),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // --- AFIȘARE STOCURI CURENTE ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("STOCURI CURENTE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        culoriDisponibile.forEach { culoare ->
                            val stoc = stocuriCurent[culoare] ?: 0
                            val culoareBg = when(culoare) {
                                "Yellow" -> Color(0xFFFFD700)
                                "Magenta" -> Color(0xFFFF00FF)
                                "Cyan" -> Color(0xFF00FFFF)
                                "Black" -> Color(0xFF000000)
                                else -> Color.Gray
                            }
                            val cantitateText = when(culoare) {
                                "Cyan" -> cantitateCyan
                                "Magenta" -> cantitateMagenta
                                "Yellow" -> cantitateYellow
                                "Black" -> cantitateBlack
                                else -> ""
                            }
                            val progres = (stoc.toFloat() / stocMaxim.toFloat()).coerceIn(0f, 1f)
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                // Bară de progres verticală
                                Box(
                                    modifier = Modifier
                                        .width(45.dp)
                                        .height(150.dp)
                                        .background(Color(0xFFE9ECEF), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((progres * 150).dp)
                                            .background(culoareBg, RoundedCornerShape(8.dp))
                                            .align(Alignment.BottomCenter)
                                    )
                                    Text(
                                        text = "$stoc",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = if (culoare == "Black") Color.Red else Color.Black,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = culoare,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = cantitateText,
                                    onValueChange = { 
                                        when(culoare) {
                                            "Cyan" -> cantitateCyan = it
                                            "Magenta" -> cantitateMagenta = it
                                            "Yellow" -> cantitateYellow = it
                                            "Black" -> cantitateBlack = it
                                        }
                                    },
                                    label = { Text("") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(60.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Butoane comune intrare/ieșire
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                culoriDisponibile.forEach { culoare ->
                                    val cantitate = when(culoare) {
                                        "Cyan" -> cantitateCyan.toIntOrNull() ?: 0
                                        "Magenta" -> cantitateMagenta.toIntOrNull() ?: 0
                                        "Yellow" -> cantitateYellow.toIntOrNull() ?: 0
                                        "Black" -> cantitateBlack.toIntOrNull() ?: 0
                                        else -> 0
                                    }
                                    if (cantitate > 0) {
                                        inregistreazaTranzactie(culoare, cantitate, "intrare")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754)),
                            enabled = !seIncarca && (cantitateCyan.isNotEmpty() || cantitateMagenta.isNotEmpty() || cantitateYellow.isNotEmpty() || cantitateBlack.isNotEmpty())
                        ) {
                            Text("📥 INTRARE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Button(
                            onClick = {
                                culoriDisponibile.forEach { culoare ->
                                    val cantitate = when(culoare) {
                                        "Cyan" -> cantitateCyan.toIntOrNull() ?: 0
                                        "Magenta" -> cantitateMagenta.toIntOrNull() ?: 0
                                        "Yellow" -> cantitateYellow.toIntOrNull() ?: 0
                                        "Black" -> cantitateBlack.toIntOrNull() ?: 0
                                        else -> 0
                                    }
                                    if (cantitate > 0) {
                                        inregistreazaTranzactie(culoare, cantitate, "iesire")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                            enabled = !seIncarca && (cantitateCyan.isNotEmpty() || cantitateMagenta.isNotEmpty() || cantitateYellow.isNotEmpty() || cantitateBlack.isNotEmpty())
                        ) {
                            Text("📤 IEȘIRE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        
        // --- ZONĂ SCROLLABILĂ - ISTORIC ---
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "ISTORIC TRANZACȚII",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (istoricTranzactii.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5))
                    ) {
                        Text(
                            text = "Nu există tranzacții înregistrate.",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            
            items(istoricTranzactii) { tranzactie ->
                val dataOra = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale("ro"))
                    .format(java.util.Date(tranzactie.timestamp))
                
                val culoareBg = when(tranzactie.culoare) {
                    "Yellow" -> Color(0xFFFFD700)
                    "Magenta" -> Color(0xFFFF00FF)
                    "Cyan" -> Color(0xFF00FFFF)
                    "Black" -> Color(0xFF000000)
                    else -> Color.Gray
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(culoareBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tranzactie.culoare.take(2),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = if (tranzactie.culoare == "Black") Color.White else Color.Black
                                )
                            }
                            
                            Column {
                                Text(
                                    text = if (tranzactie.tip == "intrare") "📥 INTRARE" else "📤 IEȘIRE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (tranzactie.tip == "intrare") Color(0xFF198754) else Color(0xFFDC3545)
                                )
                                Text(
                                    text = "${tranzactie.cantitate} unități ${tranzactie.culoare}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                                if (tranzactie.observatii.isNotEmpty()) {
                                    Text(
                                        text = tranzactie.observatii,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = dataOra,
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = tranzactie.operator,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}
