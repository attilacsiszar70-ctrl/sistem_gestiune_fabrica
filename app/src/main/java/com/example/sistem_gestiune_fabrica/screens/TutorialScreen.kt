package com.example.sistem_gestiune_fabrica.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    userData: Map<String, Any>,
    onBackClick: () -> Unit
) {
    val numeUser = userData["nume"]?.toString() ?: "Utilizator"
    val functieUser = userData["functie"]?.toString() ?: "Operator"
    
    // Obține drepturile
    val drepturiObiectBrut = userData["drepturiFerestre"]
    val drepturiFerestre = if (drepturiObiectBrut is Map<*, *>) {
        drepturiObiectBrut.mapKeys { it.key.toString() }.mapValues { it.value }
    } else emptyMap<String, Any>()
    
    val areDreptAdmin = drepturiFerestre["administrare_drepturi"] as? Boolean ?: false
    val areDreptPlanuri = drepturiFerestre["lansare_planuri"] as? Boolean ?: false
    val areDreptStocuri = drepturiFerestre["stocuri"] as? Boolean ?: false
    val areDreptNomenclator = drepturiFerestre["retete_nomenclator"] as? Boolean ?: false
    val areDreptRapoarte = drepturiFerestre["rapoarte_analiza"] as? Boolean ?: false
    val areDreptDashboard = drepturiFerestre["dashboard"] as? Boolean ?: false
    val areDreptGatuiri = drepturiFerestre["detector_gatuiri"] as? Boolean ?: false
    val areDreptCerneluri = drepturiFerestre["gestiune_cerneluri"] as? Boolean ?: false
    
    // Verificare drepturi ateliere
    val drepturiAteliere = (userData["drepturiAteliere"] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap<String, Any>()
    val areAccesAtelier0 = drepturiAteliere["Atelier0"] == true || drepturiAteliere["Atelier0"]?.toString() == "true"
    val areAccesAtelier1 = drepturiAteliere["Atelier1"] == true || drepturiAteliere["Atelier1"]?.toString() == "true"
    val areAccesAtelier2 = drepturiAteliere["Atelier2"] == true || drepturiAteliere["Atelier2"]?.toString() == "true"
    val areAccesAtelier3 = drepturiAteliere["Atelier3"] == true || drepturiAteliere["Atelier3"]?.toString() == "true"
    val areAccesAtelier4 = drepturiAteliere["Atelier4"] == true || drepturiAteliere["Atelier4"]?.toString() == "true"
    val areAccesOriceAtelier = areAccesAtelier0 || areAccesAtelier1 || areAccesAtelier2 || areAccesAtelier3 || areAccesAtelier4
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7FB))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📚 INSTRUCȚIUNI UTILIZARE",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0D6EFD)
                )
                Text(
                    text = "Salut, $numeUser! ($functieUser)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Conținut scrollabil
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card general
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "👋 BUN VENIT ÎN SISTEMUL DE GESTIUNE",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = Color(0xFF0D6EFD)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Acest sistem vă ajută să gestionați producția, stocurile și trasabilitatea în fabrică. Mai jos găsiți instrucțiuni personalizate pentru rolul dumneavoastră.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }
            }
            
            // Raportare Productie (pentru utilizatorii cu acces la ateliere)
            if (areAccesOriceAtelier) {
                CardInstructiuni(
                    titlu = "📝 RAPORTARE PRODUCȚIE",
                    icon = "📝",
                    culoare = Color(0xFF00695C),
                    instructiuni = listOf(
                        "Accesați secțiunea de raportare prin click pe cardul atelierului dumneavoastră",
                        "Selectați planul de producție activ din lista disponibilă",
                        "Introduceți cantitatea de piese produse în câmpul 'Cantitate OK'",
                        "Puteți înregistra și piese necorespunzătoare în câmpul 'Cantitate NOK'",
                        "Adăugați observații sau probleme întâmpinate în secțiunea de comentarii",
                        "Confirmați raportarea pentru a salva datele în sistem",
                        "Datele sunt salvate în timp real și pot fi vizualizate în rapoarte",
                        "Puteți vedea istoricul raportărilor dumneavoastră în Fișa Zilnică"
                    )
                )
            }
            
            // Lansare Planuri
            if (areDreptPlanuri) {
                CardInstructiuni(
                    titlu = "📋 LANSARE PLANURI DE PRODUCȚIE",
                    icon = "📋",
                    culoare = Color(0xFF1976D2),
                    instructiuni = listOf(
                        "Accesați secțiunea 'Lansare Planuri' din meniul principal",
                        "Selectați modelul de scaun pe care doriți să îl produceți din nomenclator",
                        "Introduceți cantitatea de scaune dorită pentru producție",
                        "Sistemul va calcula automat toate componentele necesare din rețetă",
                        "Verificați stocurile disponibile înainte de lansare",
                        "Confirmați lansarea planului pentru a începe producția",
                        "Planul va fi vizibil în toate atelierele pentru raportare",
                        "Puteți monitoriza progresul în timp real în secțiunea de trasabilitate",
                        "Planurile pot fi modificate sau anulate dacă nu au început producția"
                    )
                )
            }
            
            // Stocuri
            if (areDreptStocuri) {
                CardInstructiuni(
                    titlu = "📦 GESTIUNE STOCURI",
                    icon = "📦",
                    culoare = Color(0xFF388E3C),
                    instructiuni = listOf(
                        "Accesați secțiunea 'Gestiune Stocuri' din meniul principal",
                        "Puteți vedea stocul curent pentru toate componentele din fabrică",
                        "Adăugați stoc folosind butonul '+' și introducând cantitatea",
                        "Scădeți stocul folosind butonul '-' pentru ieșiri sau consum",
                        "Stocul este actualizat în timp real pentru toate atelierele",
                        "Puteți filtra componentele după atelier sau categorie",
                        "Sistemul avertizează când stocul este sub nivelul critic",
                        "Puteți vedea istoricul mișcărilor de stoc pentru fiecare componentă",
                        "Stocurile sunt sincronizate cu raportările de producție"
                    )
                )
            }
            
            // Nomenclator/Rețete
            if (areDreptNomenclator) {
                CardInstructiuni(
                    titlu = "🔧 NOMENCLATOR ȘI REȚETE",
                    icon = "🔧",
                    culoare = Color(0xFF7B1FA2),
                    instructiuni = listOf(
                        "Accesați secțiunea 'Nomenclator' din meniul principal",
                        "Puteți crea sau modifica rețete pentru modelele de scaune",
                        "Adăugați componente noi în rețetă cu cantitățile necesare per scaun",
                        "Specificați atelierul și faza de prelucrare pentru fiecare componentă",
                        "Indicați subansamblul de destinație pentru trasabilitate",
                        "Rețetele sunt folosite automat la lansarea planurilor de producție",
                        "Puteți clona rețete existente pentru a crea modele similare",
                        "Verificați întotdeauna rețeta înainte de lansarea producției",
                        "Modificările la rețetă afectează doar planurile viitoare"
                    )
                )
            }
            
            // Rapoarte și Analiză
            if (areDreptRapoarte) {
                CardInstructiuni(
                    titlu = "📊 RAPORTE ȘI ANALIZĂ",
                    icon = "📊",
                    culoare = Color(0xFFE65100),
                    instructiuni = listOf(
                        "Accesați secțiunea 'Rapoarte' din meniul principal",
                        "Generați rapoarte de producție pentru săptămâna sau luna curentă",
                        "Selectați perioada dorită și filtrați după atelier sau operator",
                        "Exportați rapoarte în format PDF pentru partajare sau arhivare",
                        "Analizați eficiența producției și identificați problemele",
                        "Comparați performanța între diferite perioade",
                        "Vizualizați grafice de progres și statistici detaliate",
                        "Rapoartele includ cantități OK/NOK și timp de lucru",
                        "Puteți vedea top operatori și ateliere performante"
                    )
                )
            }
            
            // Dashboard / Gâturi
            if (areDreptDashboard || areDreptGatuiri) {
                CardInstructiuni(
                    titlu = "🔍 DASHBOARD ȘI DETECTARE GÂTURI",
                    icon = "🔍",
                    culoare = Color(0xFFD32F2F),
                    instructiuni = listOf(
                        "Accesați secțiunea 'Dashboard' din meniul principal",
                        "Vizualizați progresul în timp real pentru toate atelierele",
                        "Sistemul identifică automat gâturile (bottlenecks) în producție",
                        "Reperul critic este evidențiat cu roșu în fluxul tehnologic",
                        "Puteți vedea trasabilitatea completă pentru fiecare componentă",
                        "Click pe un reper pentru a vedea detalii despre stoc și producție",
                        "Exportați rapoarte PDF pentru toate produsele active",
                        "Sistemul arată procentul de completare pentru fiecare atelier",
                        "Identificați rapid care atelier blochează producția"
                    )
                )
            }
            
            // Gestiune Cerneluri
            if (areDreptCerneluri) {
                CardInstructiuni(
                    titlu = "🎨 GESTIUNE CERNELURI IMPRIMARE",
                    icon = "🎨",
                    culoare = Color(0xFF9C27B0),
                    instructiuni = listOf(
                        "Accesați secțiunea 'Gestiune Cerneluri' din meniul principal",
                        "Monitorizați stocurile de cerneluri: Cyan, Magenta, Yellow, Black",
                        "Barele de progres verticale arată nivelul curent al stocului",
                        "Introduceți cantitatea în textbox-ul de sub fiecare culoare",
                        "Apăsați 'IN' pentru intrare sau 'OUT' pentru ieșire",
                        "Sistemul avertizează automat când stocul scade sub 20 unități",
                        "Puteți înregistra cantități pentru mai multe culori simultan",
                        "Istoricul tranzacțiilor este afișat în partea de jos a ecranului",
                        "Stocul este calculat automat din intrări și ieșiri"
                    )
                )
            }
            
            // Administrare Drepturi
            if (areDreptAdmin) {
                CardInstructiuni(
                    titlu = "👤 ADMINISTRARE DREPTURI UTILIZATORI",
                    icon = "👤",
                    culoare = Color(0xFF607D8B),
                    instructiuni = listOf(
                        "Accesați secțiunea 'Administrare Drepturi' din meniul principal",
                        "Puteți crea conturi noi pentru utilizatori",
                        "Introduceți numele, username-ul și funcția utilizatorului",
                        "Atribuiți sau retrageți drepturi specifice pentru fiecare utilizator",
                        "Drepturi disponibile: Planuri, Stocuri, Nomenclator, Rapoarte, Dashboard, Cerneluri",
                        "Atribuiți acces la ateliere specifice pentru raportare",
                        "Utilizatorii cu rolul Admin sau Maistru au acces extins",
                        "Modificările sunt salvate imediat în sistem",
                        "Puteți dezactiva conturi fără a le șterge definitiv"
                    )
                )
            }
            
            // Card general pentru utilizatori fără drepturi specifice
            if (!areDreptPlanuri && !areDreptStocuri && !areDreptNomenclator && !areDreptRapoarte && !areDreptDashboard && !areDreptGatuiri && !areDreptCerneluri && !areDreptAdmin && !areAccesOriceAtelier) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ℹ️ ACCES LIMITAT",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color(0xFF856404)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "În acest moment nu aveți drepturi specifice atribuite. Contactați administratorul sistemului pentru a obține acces la funcționalitățile aplicației.",
                            fontSize = 13.sp,
                            color = Color(0xFF856404)
                        )
                    }
                }
            }
            
            // Card suport
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "❓ NEVOI DE AJUTOR?",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color(0xFF0D47A1)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pentru întrebări sau probleme tehnice, contactați administratorul sistemului sau echipa IT.",
                        fontSize = 12.sp,
                        color = Color(0xFF0D47A1)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun CardInstructiuni(
    titlu: String,
    icon: String,
    culoare: Color,
    instructiuni: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = titlu,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = culoare
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            instructiuni.forEachIndexed { index, instructiune ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${index + 1}.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = culoare
                    )
                    Text(
                        text = instructiune,
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (index < instructiuni.size - 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
