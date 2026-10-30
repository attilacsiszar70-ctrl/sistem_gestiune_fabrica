package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun DashboardAnalyticsScreen(firestore: FirebaseFirestore, onBackClick: () -> Unit) {
    var perioadaSelectata by remember { mutableStateOf("2026-W41") }
    var listaStocuri by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(perioadaSelectata) {
        firestore.collection("stocuri_faze").whereEqualTo("perioada", perioadaSelectata).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) listaStocuri = snapshot.documents.map { it.data ?: emptyMap() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Dashboard Producție", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
        Spacer(modifier = Modifier.height(16.dp))
        val gatuiri = listaStocuri.filter { ((it["stocDisponibil"] as? Long)?.toInt() ?: 0) > 500 }
        Text("ALERTE GÂTUIRI:", fontWeight = FontWeight.Bold, color = Color.Red)
        if (gatuiri.isEmpty()) Text("Flux normal.", color = Color(0xFF2E7D32)) else {
            gatuiri.forEach { b -> Text("Blocaj la ${b["faza"]}! (${b["stocDisponibil"]} buc)", color = Color.Red, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Stocuri active:")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(listaStocuri) { s -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${s["faza"]}"); Text("${s["stocDisponibil"]} buc", fontWeight = FontWeight.Bold) } } }
        }
    }
}
