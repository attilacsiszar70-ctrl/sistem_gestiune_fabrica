package com.example.sistem_gestiune_fabrica.screens

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LansarePlanScreen(
    firestore: FirebaseFirestore,
    onBackClick: () -> Unit
) {
    var listaToateModelele by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var modelSelectatPtLansare by remember { mutableStateOf<Map<String, Any>?>(null) }

    var cantitateScauneText by remember { mutableStateOf("") }
    var statusMesajText by remember { mutableStateOf("") }
    var seIncarca by remember { mutableStateOf(false) }

    var planDeEditatAcum by remember { mutableStateOf<Map<String, Any>?>(null) }
    var cantitateEditataText by remember { mutableStateOf("") }
    var stareEditataSelectata by remember { mutableStateOf("In Lucru") }

    LaunchedEffect(Unit) {
        firestore.collection("comenzi_productie").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                listaToateModelele = snapshot.documents.map { (it.data ?: emptyMap<String, Any>()) + ("idDoc" to it.id) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. BARĂ TITLU ȘI NAVIGARE SECURED ---
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lansare Planuri",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                Text("Trimiterea, suplimentarea și managementul loturilor de scaune", fontSize = 13.sp, color = Color.Gray)
                Divider(color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
            }
        }

        // --- 2. SELECTOR PENTRU MODEL ---
        item {
            Text("1. Alegeți un Scaun pentru lansare sau suplimentare lot:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            val modeleDistincte = listaToateModelele.distinctBy { it["modelId"]?.toString() }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2
            ) {
                modeleDistincte.forEach { model ->
                    val mNume = model["modelNume"]?.toString() ?: "Scaun"
                    val mId = model["modelId"]?.toString() ?: ""
                    val esteSelectat = modelSelectatPtLansare?.get("modelId")?.toString() == mId

                    Box(modifier = Modifier.weight(1f)) {
                        FilterChip(
                            selected = esteSelectat,
                            onClick = { modelSelectatPtLansare = model; planDeEditatAcum = null; statusMesajText = "" },
                            label = {
                                Text(
                                    mNume.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (modeleDistincte.size % 2 != 0) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // --- 3. FORMULAR LANSARE / CUMULARE LIVE LOT NOU ---
        item {
            modelSelectatPtLansare?.let { modelActiv ->
                val mNumeCurent = modelActiv["modelNume"]?.toString() ?: "Scaun"
                val mIdCurent = modelActiv["modelId"]?.toString() ?: ""
                val cantitateExistentaInFabricatie = (modelActiv["cantitateScaune"] as? Long)?.toInt() ?: 0
                val stareaCurenta = modelActiv["stare"]?.toString() ?: "In Asteptare"

                Card(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (stareaCurenta == "In Lucru" && cantitateExistentaInFabricatie > 0) {
                            Text(text = "🔄 SUPLIMENTARE LOT ACTIV: ${mNumeCurent.uppercase()}", fontWeight = FontWeight.Black, color = Color(0xFFE65100), fontSize = 13.sp)
                            Text(text = "În acest moment se află deja în producție un lot de $cantitateExistentaInFabricatie buc. Cantitatea introdusă mai jos se va adăuga la cea existentă.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        } else {
                            Text(text = "🏭 LANSARE LOT NOU: ${mNumeCurent.uppercase()}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = cantitateScauneText,
                            onValueChange = { cantitateScauneText = it },
                            label = { Text(if (stareaCurenta == "In Lucru") "Introduceți cantitatea de ADĂUGAT (Ex: 50)" else "Introduceți cantitatea totală a lotului") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val cantitateNouIntrodusa = cantitateScauneText.toIntOrNull() ?: 0
                                if (cantitateNouIntrodusa <= 0) { statusMesajText = "Introduceți o cantitate validă!"; return@Button }
                                seIncarca = true

                                // LOGICA DE CUMULARE AUTOMATĂ: dacă e deja în lucru, adunăm valorile
                                val tintaFinalaCalculata = if (stareaCurenta == "In Lucru") {
                                    cantitateExistentaInFabricatie + cantitateNouIntrodusa
                                } else {
                                    cantitateNouIntrodusa
                                }

                                val actualizarePlanMap = mapOf(
                                    "cantitateScaune" to tintaFinalaCalculata,
                                    "stare" to "In Lucru",
                                    "timestampLansare" to System.currentTimeMillis()
                                )

                                firestore.collection("comenzi_productie").document(mIdCurent).set(actualizarePlanMap, SetOptions.merge())
                                    .addOnSuccessListener {
                                        seIncarca = false; cantitateScauneText = ""; modelSelectatPtLansare = null
                                        statusMesajText = if (stareaCurenta == "In Lucru") {
                                            "Succes! Lotul pentru '$mNumeCurent' a fost suplimentat cu $cantitateNouIntrodusa buc. Țintă nouă totală: $tintaFinalaCalculata buc! 🚀"
                                        } else {
                                            "Succes! Modelul '$mNumeCurent' a fost lansat live cu un lot de $cantitateNouIntrodusa bucăți!"
                                        }
                                    }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            enabled = !seIncarca,
                            colors = ButtonDefaults.buttonColors(containerColor = if (stareaCurenta == "In Lucru") Color(0xFFE65100) else MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (stareaCurenta == "In Lucru") "ADAUGĂ CANTITATE LA LOTUL ACTIV ➕" else "LANSEAZĂ PRODUCȚIA LOTULUI 🚀", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 4. FORMULAR MODIFICARE / EDITARE PLAN EXISTENT ---
        item {
            planDeEditatAcum?.let { planActiv ->
                val mNumeEdit = planActiv["modelNume"]?.toString() ?: "Scaun"
                val mIdEdit = planActiv["modelId"]?.toString() ?: ""

                Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE65100), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "✏️ MODIFICARE FORMAT PLANĂ: ${mNumeEdit.uppercase()}", fontWeight = FontWeight.Black, color = Color(0xFFE65100), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = cantitateEditataText, onValueChange = { cantitateEditataText = it }, label = { Text("Modifică manual cantitatea țintă totală") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Modifică status producție lot:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("In Lucru", "In Asteptare", "Finalizata").forEach { st ->
                                FilterChip(selected = stareEditataSelectata == st, onClick = { stareEditataSelectata = st }, label = { Text(st, fontSize = 11.sp) })
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { planDeEditatAcum = null }, modifier = Modifier.weight(1f)) { Text("ANULEAZĂ") }
                            Button(
                                onClick = {
                                    val qEdit = cantitateEditataText.toIntOrNull() ?: 0
                                    if (qEdit < 0) { statusMesajText = "Introduceți o cantitate validă!"; return@Button
                                    }
                                    seIncarca = true
                                    val dateUpdate = mutableMapOf<String, Any>(
                                        "cantitateScaune" to qEdit,
                                        "stare" to stareEditataSelectata
                                    )
                                    // Dacă trecem din nou în "In Lucru" sau lansăm o cantitate nouă, resetăm timestampLansare
                                    val stareVeche = planActiv["stare"]?.toString() ?: ""
                                    if (stareEditataSelectata == "In Lucru" && (stareVeche != "In Lucru" || planActiv["timestampLansare"] == null)) {
                                        dateUpdate["timestampLansare"] = System.currentTimeMillis()
                                    }
                                    firestore.collection("comenzi_productie").document(mIdEdit).set(dateUpdate, SetOptions.merge())
                                        .addOnSuccessListener { seIncarca = false; planDeEditatAcum = null; statusMesajText = "Planul '$mNumeEdit' a fost actualizat live!" }
                                },
                                modifier = Modifier.weight(1.5f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)), enabled = !seIncarca
                            ) { Text("SALVEAZĂ MODIFICĂRILE 💾", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
// --- 5. ETICHETĂ STATUS MESAJ ---
        item {
            if (statusMesajText.isNotEmpty()) {
                Text(text = statusMesajText, color = Color(0xFF198754), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
// --- 6. TITLU CENTRALIZATOR ---
        item {
            Text(text = "Centralizator Loturi Active și Stadiul lor în Fabrică:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
// --- 7. LISTARE FORMULAR CONTROL PLANURI ---
        items(listaToateModelele) { plan ->
            val pId = plan["modelId"]?.toString() ?: ""
            val pNume = plan["modelNume"]?.toString() ?: "Scaun"
            val pQty = (plan["cantitateScaune"] as? Long)?.toInt() ?: 0
            val pStare = plan["stare"]?.toString() ?: "In Asteptare"
            val culoareStatus = when(pStare) {
                "In Lucru" -> Color(0xFF198754)
                "In Asteptare" -> Color(0xFFE65100)
                else -> Color(0xFF6c757d)
            }
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = pNume.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Status: ", fontSize = 12.sp, color = Color.Gray)
                            Text(text = pStare, fontSize = 12.sp, fontWeight = FontWeight.Black, color = culoareStatus)
                            Text(text = " | Volum Lot: $pQty buc", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                planDeEditatAcum = plan; modelSelectatPtLansare = null
                                cantitateEditataText = pQty.toString(); stareEditataSelectata = pStare; statusMesajText = ""
                            },
                            shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text("Modifică ✏️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100)) }
                        Button(
                            onClick = {
                                seIncarca = true
                                firestore.collection("comenzi_productie").document(pId).set(mapOf("cantitateScaune" to 0, "stare" to "In Asteptare"), SetOptions.merge())
                                    .addOnSuccessListener { seIncarca = false; statusMesajText = "Planul '$pNume' a fost resetat/sters de pe linie!" }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), enabled = !seIncarca
                        ) { Text("Șterge 🗑️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    }
                }
            }
        }
    }
}